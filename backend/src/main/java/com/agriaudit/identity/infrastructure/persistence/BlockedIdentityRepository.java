package com.agriaudit.identity.infrastructure.persistence;

import com.agriaudit.identity.domain.BlockedIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BlockedIdentityRepository extends JpaRepository<BlockedIdentity, Long> {

    List<BlockedIdentity> findByDestroyAfterBefore(Instant instant);

    long countByOrganizationId(Long organizationId);

    Optional<BlockedIdentity> findFirstByOrganizationIdOrderByDestroyAfterAsc(Long organizationId);
}
