package com.agriaudit.fieldbook.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface FarmJpaRepository extends JpaRepository<FarmJpaEntity, Long> {
    List<FarmJpaEntity> findByOrganizationIdOrderByLegalNameAsc(Long organizationId);
    Optional<FarmJpaEntity> findByIdAndOrganizationId(Long id, Long organizationId);
    List<FarmJpaEntity> findByOrganizationIdAndIdIn(Long organizationId, Collection<Long> ids);
    long countByOrganizationId(Long organizationId);
}
