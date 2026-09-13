package com.agriaudit.audit.infrastructure.persistence;

import com.agriaudit.audit.domain.AuditItemResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditItemResultRepository extends JpaRepository<AuditItemResult, Long> {
    List<AuditItemResult> findByAuditId(Long auditId);
    Optional<AuditItemResult> findByAuditIdAndControlPointId(Long auditId, Long controlPointId);
}
