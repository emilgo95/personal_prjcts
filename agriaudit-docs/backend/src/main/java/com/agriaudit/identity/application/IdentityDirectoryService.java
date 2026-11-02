package com.agriaudit.identity.application;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.identity.OrganizationDirectory;
import com.agriaudit.identity.Role;
import com.agriaudit.identity.UserProvisioning;
import com.agriaudit.identity.domain.AppUser;
import com.agriaudit.identity.domain.Organization;
import com.agriaudit.identity.infrastructure.persistence.AppUserRepository;
import com.agriaudit.identity.infrastructure.persistence.OrganizationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
class IdentityDirectoryService implements OrganizationDirectory, UserProvisioning {

    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    IdentityDirectoryService(OrganizationRepository organizations, AppUserRepository users,
                             PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<String> organizationName(Long organizationId) {
        return organizations.findById(organizationId).map(Organization::getName);
    }

    @Override
    public Optional<Long> findOrganizationIdByName(String name) {
        return organizations.findByName(name).map(Organization::getId);
    }

    @Override
    public Optional<Long> anyOrganizationId() {
        return organizations.findAll().stream().findFirst().map(Organization::getId);
    }

    @Override
    @Transactional
    public void ensureUser(Long organizationId, String email, String fullName, Role role, String rawPassword) {
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        Organization org = organizations.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organización no encontrada: " + organizationId));
        AppUser u = new AppUser();
        u.setOrganization(org);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(role);
        u.setActive(true);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        users.save(u);
    }
}
