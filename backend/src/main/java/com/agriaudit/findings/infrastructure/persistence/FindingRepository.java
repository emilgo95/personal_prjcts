package com.agriaudit.findings.infrastructure.persistence;

import com.agriaudit.findings.domain.Finding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByAuditIdOrderByClassificationAscIdAsc(Long auditId);
    Optional<Finding> findByIdAndAuditId(Long id, Long auditId);
    long countByAuditId(Long auditId);

    List<Finding> findByAuditIdIn(Collection<Long> auditIds);
}
