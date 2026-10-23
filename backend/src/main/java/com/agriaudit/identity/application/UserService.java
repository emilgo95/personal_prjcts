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

    public UserService(AppUserRepository users, OrganizationRepository organizations,
                       PasswordEncoder passwordEncoder) {
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
        return toDto(users.save(user));
    }

    public UserDto update(Long id, UserUpdateRequest request) {
        AppUser user = users.findById(id)
                .filter(u -> u.getOrganization().getId().equals(SecurityUtils.currentOrganizationId()))
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + id));
        Role newRole = parseRole(request.role());
        boolean newActive = request.active() != null ? request.active() : user.isActive();
        guardAdministrators(user, newRole, newActive);
        user.setFullName(request.fullName());
        user.setRole(newRole);
        user.setActive(newActive);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toDto(users.save(user));
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

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Rol no válido: " + value);
        }
    }

    private UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(),
                u.getOrganization().getId(), u.getOrganization().getName());
    }
}
