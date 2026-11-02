package com.agriaudit.ai.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AiProposalJpaRepository extends JpaRepository<AiProposalJpaEntity, Long> {

    Optional<AiProposalJpaEntity> findByIdAndOrganizationId(Long id, Long organizationId);

    List<AiProposalJpaEntity> findByOrganizationIdAndStatus(Long organizationId, String status, Pageable page);

    long countByOrganizationIdAndStatus(Long organizationId, String status);
}
