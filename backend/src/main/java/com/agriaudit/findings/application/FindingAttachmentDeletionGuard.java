package com.agriaudit.findings.application;

import com.agriaudit.audit.AuditLookup;
import com.agriaudit.common.ConflictException;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import com.agriaudit.storage.AttachmentDeletionGuard;
import com.agriaudit.storage.AttachmentEntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** No deja borrar evidencias de una no conformidad cuya auditoría está enviada o cerrada. */
@Component
class FindingAttachmentDeletionGuard implements AttachmentDeletionGuard {

    private final FindingRepository findings;
    private final AuditLookup audits;

    FindingAttachmentDeletionGuard(FindingRepository findings, AuditLookup audits) {
        this.findings = findings;
        this.audits = audits;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanDelete(AttachmentEntityType type, Long entityId) {
        if (type != AttachmentEntityType.FINDING) {
            return;
        }
        findings.findById(entityId).ifPresent(f -> {
            var audit = audits.requireAudit(f.getAuditId());
            if (audit.status().isLocked()) {
                throw new ConflictException(audit.status().lockedMessage());
            }
        });
    }
}
