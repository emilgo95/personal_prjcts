package com.agriaudit.identity.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.identity.domain.AppUser;
import com.agriaudit.identity.domain.Organization;
import com.agriaudit.identity.Role;
import com.agriaudit.identity.infrastructure.persistence.AppUserRepository;
import com.agriaudit.identity.infrastructure.persistence.OrganizationRepository;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.identity.application.AuthDtos.UserDto;
import com.agriaudit.identity.application.UserDtos.UserCreateRequest;
import com.agriaudit.identity.application.UserDtos.UserUpdateRequest;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import com.agriaudit.identity.IdentityBlockListener;
import com.agriaudit.identity.infrastructure.persistence.BlockedIdentityRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final ChangeRecorder trace;
    private final ObjectProvider<IdentityBlockListener> blockListeners;
    private final BlockedIdentityRepository blockedIdentities;
    private final PrivacyRetention retention;

    public UserService(AppUserRepository users, OrganizationRepository organizations,
                       PasswordEncoder passwordEncoder, ChangeRecorder trace,
                       ObjectProvider<IdentityBlockListener> blockListeners,
                       BlockedIdentityRepository blockedIdentities, PrivacyRetention retention) {
        this.blockListeners = blockListeners;
        this.blockedIdentities = blockedIdentities;
        this.retention = retention;
        this.trace = trace;
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return users.findByOrganizationIdOrderByEmailAsc(SecurityUtils.currentOrganizationId())
                .stream().map(this::toDto).toList();
    }

    public UserDto create(UserCreateRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("Ya existe un usuario con ese correo");
        }
        Organization org = organizations.findById(SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Organización no encontrada"));
        AppUser user = new AppUser();
        user.setOrganization(org);
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRole(parseRole(request.role()));
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        AppUser saved = users.save(user);
        // La traza es inalterable: no guarda el nombre ni el correo, solo el código del usuario.
        trace.record(ChangeEntry.of(TraceEntity.USER, saved.getId(), "usuario " + saved.getId(), null,
                TraceAction.CREATE,
                "Creó el usuario n.º " + saved.getId() + " con el rol «" + roleLabel(saved.getRole()) + "»",
                Changes.create().personalInitial("email", "Correo", saved.getEmail())
                        .personalInitial("fullName", "Nombre", saved.getFullName())
                        .initial("role", "Rol", roleLabel(saved.getRole())).list()));
        return toDto(saved);
    }

    public UserDto update(Long id, UserUpdateRequest request) {
        AppUser user = users.findById(id)
                .filter(u -> u.getOrganization().getId().equals(SecurityUtils.currentOrganizationId()))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
        Role newRole = parseRole(request.role());
        boolean newActive = request.active() != null ? request.active() : user.isActive();
        if (user.isBlocked()) {
            throw new ConflictException("Los datos de este usuario están bloqueados: la cuenta no se puede modificar.");
        }
        guardAdministrators(user, newRole, newActive);
        String oldName = user.getFullName();
        Role oldRole = user.getRole();
        boolean oldActive = user.isActive();
        user.setFullName(request.fullName());
        user.setRole(newRole);
        user.setActive(newActive);
        boolean passwordChanged = request.password() != null && !request.password().isBlank();
        if (passwordChanged) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        AppUser saved = users.save(user);
        Changes c = Changes.create()
                .personal("fullName", "Nombre", oldName, saved.getFullName())
                .track("role", "Rol", roleLabel(oldRole), roleLabel(saved.getRole()))
                .track("active", "Estado", oldActive ? "Activo" : "Desactivado",
                        saved.isActive() ? "Activo" : "Desactivado");
        if (passwordChanged) {
            c.track("password", "Contraseña", "(anterior)", "(restablecida)");
        }
        if (!c.isEmpty()) {
            trace.record(ChangeEntry.of(TraceEntity.USER, saved.getId(), "usuario " + saved.getId(), null,
                    TraceAction.UPDATE, "Modificó el usuario n.º " + saved.getId(), c.list()));
        }
        return toDto(saved);
    }

    /**
     * Impide dejar la organización sin administrador: nadie puede quitarse a sí mismo el rol de administrador ni
     * desactivar su propia cuenta, y siempre debe quedar al menos un administrador activo.
     */
    private void guardAdministrators(AppUser user, Role newRole, boolean newActive) {
        boolean isActiveAdmin = user.getRole() == Role.ADMIN && user.isActive();
        boolean staysActiveAdmin = newRole == Role.ADMIN && newActive;
        if (!isActiveAdmin || staysActiveAdmin) {
            return;
        }
        if (user.getId().equals(SecurityUtils.currentUserId())) {
            throw new ConflictException(
                    "No puedes quitarte el rol de administrador ni desactivar tu propia cuenta. Pídeselo a otro administrador.");
        }
        long activeAdmins = users.countByOrganizationIdAndRoleAndActive(
                user.getOrganization().getId(), Role.ADMIN, true);
        if (activeAdmins <= 1) {
            throw new ConflictException("La organización debe conservar al menos un administrador activo.");
        }
    }

    /**
     * Bloquea los datos identificativos de un usuario (art. 32 LOPDGDD): su nombre y correo dejan de mostrarse y de
     * tratarse en toda la aplicación, la cuenta queda inutilizable y los datos originales se reservan, sin acceso desde
     * la aplicación, hasta que venza el plazo de conservación de la organización y se destruyan.
     */
    public UserDto block(Long id) {
        AppUser user = users.findById(id)
                .filter(u -> u.getOrganization().getId().equals(SecurityUtils.currentOrganizationId()))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
        if (user.isBlocked()) {
            throw new ConflictException("Los datos de este usuario ya están bloqueados.");
        }
        guardAdministrators(user, user.getRole() == Role.ADMIN ? Role.VIEWER : user.getRole(), false);
        Long orgId = user.getOrganization().getId();
        String oldEmail = user.getEmail();
        String oldName = user.getFullName();
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant destroyAfter = now.atZone(java.time.ZoneOffset.UTC).plusMonths(retention.monthsFor(orgId))
                .toInstant();
        blockedIdentities.save(new com.agriaudit.identity.domain.BlockedIdentity(id, orgId, oldEmail, oldName, now,
                SecurityUtils.currentUserId(), destroyAfter));

        String placeholder = "Usuario bloqueado";
        user.setEmail("bloqueado-" + id + "@bloqueado.invalid");
        user.setFullName(placeholder);
        user.setActive(false);
        user.setBlocked(true);
        user.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        AppUser saved = users.save(user);
        blockListeners.orderedStream().forEach(l -> l.identityBlocked(orgId, oldEmail, oldName, placeholder));

        trace.record(ChangeEntry.of(TraceEntity.USER, id, "usuario " + id, null, TraceAction.UPDATE,
                "Bloqueó los datos identificativos del usuario n.º " + id,
                Changes.create().track("blocked", "Datos identificativos", "Visibles", "Bloqueados").list()));
        return toDto(saved);
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "Administrador";
            case AUDITOR -> "Auditor";
            case QUALITY -> "Calidad";
            case VIEWER -> "Lector";
        };
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol no válido: " + value);
        }
    }

    /** El correo de un usuario bloqueado no se muestra: el que queda guardado es un marcador interno, no su correo. */
    private UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.isBlocked() ? null : u.getEmail(), u.getFullName(), u.getRole().name(),
                u.getOrganization().getId(), u.getOrganization().getName(), u.isActive(), u.isBlocked());
    }
}
