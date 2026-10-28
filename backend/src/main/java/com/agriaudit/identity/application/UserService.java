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

    public UserService(AppUserRepository users, OrganizationRepository organizations,
                       PasswordEncoder passwordEncoder, ChangeRecorder trace) {
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
        trace.record(ChangeEntry.of(TraceEntity.USER, saved.getId(), saved.getEmail(), null, TraceAction.CREATE,
                "Creó el usuario " + saved.getEmail() + " con el rol «" + roleLabel(saved.getRole()) + "»",
                Changes.create().initial("email", "Correo", saved.getEmail())
                        .initial("fullName", "Nombre", saved.getFullName())
                        .initial("role", "Rol", roleLabel(saved.getRole())).list()));
        return toDto(saved);
    }

    public UserDto update(Long id, UserUpdateRequest request) {
        AppUser user = users.findById(id)
                .filter(u -> u.getOrganization().getId().equals(SecurityUtils.currentOrganizationId()))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
        Role newRole = parseRole(request.role());
        boolean newActive = request.active() != null ? request.active() : user.isActive();
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
                .track("fullName", "Nombre", oldName, saved.getFullName())
                .track("role", "Rol", roleLabel(oldRole), roleLabel(saved.getRole()))
                .track("active", "Estado", oldActive ? "Activo" : "Desactivado",
                        saved.isActive() ? "Activo" : "Desactivado");
        if (passwordChanged) {
            c.track("password", "Contraseña", "(anterior)", "(restablecida)");
        }
        if (!c.isEmpty()) {
            trace.record(ChangeEntry.of(TraceEntity.USER, saved.getId(), saved.getEmail(), null, TraceAction.UPDATE,
                    "Modificó el usuario " + saved.getEmail(), c.list()));
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

    private UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(),
                u.getOrganization().getId(), u.getOrganization().getName(), u.isActive());
    }
}
