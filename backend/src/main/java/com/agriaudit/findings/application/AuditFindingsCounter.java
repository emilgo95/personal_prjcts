package com.agriaudit.findings.application;

import com.agriaudit.audit.FindingsCounter;
import com.agriaudit.findings.domain.FindingStatus;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación de {@link FindingsCounter} que declara {@code audit}. Bean propio, sin depender de
 * {@code FindingService}: este usa {@code AuditLookup}, y así no hay ciclo de beans entre los módulos.
 */
@Component
class AuditFindingsCounter implements FindingsCounter {

    private final FindingRepository findings;

    AuditFindingsCounter(FindingRepository findings) {
        this.findings = findings;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByAudit(Long auditId) {
        return findings.countByAuditId(auditId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> countByAudits(java.util.Collection<Long> auditIds) {
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (!auditIds.isEmpty()) {
            for (Object[] row : findings.countByAuditIds(auditIds)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<Long> controlPointIdsWithFinding(Long auditId) {
        return new java.util.HashSet<>(findings.findControlPointIdsByAuditId(auditId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenByAudit(Long auditId) {
        return findings.countByAuditIdAndStatusNotIn(auditId, List.of(FindingStatus.CLOSED, FindingStatus.VERIFIED));
    }
}
