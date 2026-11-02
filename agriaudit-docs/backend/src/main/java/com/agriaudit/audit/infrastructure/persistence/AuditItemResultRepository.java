package com.agriaudit.audit.infrastructure.persistence;

import com.agriaudit.audit.domain.AuditItemResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuditItemResultRepository extends JpaRepository<AuditItemResult, Long> {
    List<AuditItemResult> findByAuditId(Long auditId);

    /** Pares [id de auditoría, nº de puntos evaluados] para un conjunto de auditorías. */
    @Query("select r.auditId, count(r) from AuditItemResult r where r.auditId in :ids "
            + "and r.compliance <> com.agriaudit.audit.domain.ComplianceResult.NOT_ASSESSED group by r.auditId")
    List<Object[]> countAssessedByAudit(@Param("ids") java.util.Collection<Long> auditIds);
    Optional<AuditItemResult> findByAuditIdAndControlPointId(Long auditId, Long controlPointId);
}
