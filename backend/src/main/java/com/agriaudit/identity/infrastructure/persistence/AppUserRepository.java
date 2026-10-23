package com.agriaudit.identity.infrastructure.persistence;

import com.agriaudit.identity.Role;
import com.agriaudit.identity.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findByOrganizationIdOrderByEmailAsc(Long organizationId);

    long countByOrganizationIdAndRoleAndActive(Long organizationId, Role role, boolean active);
}
