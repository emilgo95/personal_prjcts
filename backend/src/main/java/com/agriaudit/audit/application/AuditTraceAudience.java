package com.agriaudit.audit.application;

import com.agriaudit.audit.infrastructure.persistence.AuditRepository;
import com.agriaudit.trace.TraceAudience;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/** Auditorías «suyas» de una auditora a efectos de la traza: las que creó o donde figura como auditor líder. */
@Component
class AuditTraceAudience implements TraceAudience {

    private final AuditRepository audits;

    AuditTraceAudience(AuditRepository audits) {
        this.audits = audits;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> auditIdsOf(Long organizationId, Long userId, String fullName) {
        return new HashSet<>(audits.findIdsOwnedBy(organizationId, userId,
                fullName == null || fullName.isBlank() ? null : fullName.trim()));
    }
}
