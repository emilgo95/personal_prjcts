package com.agriaudit.audit.application;

import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.audit.infrastructure.persistence.AuditRepository;
import com.agriaudit.common.ConflictException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentDeletionGuard;
import com.agriaudit.storage.AttachmentEntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** No deja borrar evidencias de una auditoría (o de sus puntos de control) enviada o cerrada. */
@Component
class AuditAttachmentDeletionGuard implements AttachmentDeletionGuard {

    private final AuditRepository audits;
    private final AuditItemResultRepository results;

    AuditAttachmentDeletionGuard(AuditRepository audits, AuditItemResultRepository results) {
        this.audits = audits;
        this.results = results;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanDelete(AttachmentEntityType type, Long entityId) {
        Long auditId = switch (type) {
            case AUDIT -> entityId;
            case AUDIT_ITEM_RESULT -> results.findById(entityId).map(r -> r.getAuditId()).orElse(null);
            default -> null;
        };
        if (auditId == null) {
            return;
        }
        audits.findByIdAndOrganizationId(auditId, SecurityUtils.currentOrganizationId())
                .filter(a -> a.getStatus().isLocked())
                .ifPresent(a -> {
                    throw new ConflictException(a.getStatus().lockedMessage());
                });
    }
}
