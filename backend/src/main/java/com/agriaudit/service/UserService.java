package com.agriaudit.service;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.domain.AppUser;
import com.agriaudit.domain.Organization;
import com.agriaudit.domain.enums.Role;
import com.agriaudit.repo.AppUserRepository;
import com.agriaudit.repo.OrganizationRepository;
import com.agriaudit.security.SecurityUtils;
import com.agriaudit.web.dto.AuthDtos.UserDto;
import com.agriaudit.web.dto.UserDtos.UserCreateRequest;
import com.agriaudit.web.dto.UserDtos.UserUpdateRequest;
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
        user.setFullName(request.fullName());
        user.setRole(parseRole(request.role()));
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toDto(users.save(user));
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
