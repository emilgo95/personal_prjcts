package com.agriaudit.findings.infrastructure.persistence;

import com.agriaudit.findings.domain.Finding;
import com.agriaudit.findings.domain.FindingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByAuditIdOrderByClassificationAscIdAsc(Long auditId);
    Optional<Finding> findByIdAndAuditId(Long id, Long auditId);
    long countByAuditId(Long auditId);
    long countByAuditIdAndStatusNotIn(Long auditId, Collection<FindingStatus> statuses);

    List<Finding> findByAuditIdIn(Collection<Long> auditIds);

    /** Pares [id de auditoría, nº de NC]. */
    @org.springframework.data.jpa.repository.Query("select f.auditId, count(f) from Finding f where f.auditId in :ids group by f.auditId")
    List<Object[]> countByAuditIds(@org.springframework.data.repository.query.Param("ids") Collection<Long> auditIds);

    @org.springframework.data.jpa.repository.Query("select distinct f.controlPointId from Finding f where f.auditId = :auditId and f.controlPointId is not null")
    List<Long> findControlPointIdsByAuditId(@org.springframework.data.repository.query.Param("auditId") Long auditId);
}
