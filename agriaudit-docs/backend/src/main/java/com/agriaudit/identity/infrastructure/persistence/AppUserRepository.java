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

    @org.springframework.data.jpa.repository.Query("select u.id from AppUser u where u.organization.id = :orgId and "
            + "(lower(u.email) like lower(concat('%', :q, '%')) or lower(coalesce(u.fullName, '')) like "
            + "lower(concat('%', :q, '%')))")
    java.util.List<Long> findIdsMatching(@org.springframework.data.repository.query.Param("orgId") Long organizationId,
                                         @org.springframework.data.repository.query.Param("q") String text);

    long countByOrganizationIdAndRoleAndActive(Long organizationId, Role role, boolean active);
}
