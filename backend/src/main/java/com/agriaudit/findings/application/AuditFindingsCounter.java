package com.agriaudit.findings.application;

import com.agriaudit.audit.FindingsCounter;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
}
