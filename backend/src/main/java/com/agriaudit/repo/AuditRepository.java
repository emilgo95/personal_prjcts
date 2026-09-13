package com.agriaudit.repo;

import com.agriaudit.domain.Audit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditRepository extends JpaRepository<Audit, Long> {
    List<Audit> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    Optional<Audit> findByIdAndOrganizationId(Long id, Long organizationId);
    long countByOrganizationId(Long organizationId);
}
