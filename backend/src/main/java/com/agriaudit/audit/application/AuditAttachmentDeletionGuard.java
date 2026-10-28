package com.agriaudit.audit.application;

import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.audit.infrastructure.persistence.AuditRepository;
import com.agriaudit.common.ConflictException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentDeletionGuard;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** No deja borrar evidencias de una auditoría (o de sus puntos de control) enviada o cerrada. */
@Component
class AuditAttachmentDeletionGuard implements AttachmentDeletionGuard {

    private final AuditRepository audits;
    private final AuditItemResultRepository results;
    private final ChangeRecorder trace;

    AuditAttachmentDeletionGuard(AuditRepository audits, AuditItemResultRepository results, ChangeRecorder trace) {
        this.audits = audits;
        this.results = results;
        this.trace = trace;
    }

    private Long auditIdOf(AttachmentEntityType type, Long entityId) {
        return switch (type) {
            case AUDIT -> entityId;
            case AUDIT_ITEM_RESULT -> results.findById(entityId).map(r -> r.getAuditId()).orElse(null);
            default -> null;
        };
    }

    @Override
    @Transactional
    public void afterDelete(AttachmentEntityType type, Long entityId, String fileName) {
        Long auditId = auditIdOf(type, entityId);
        if (auditId == null) {
            return;
        }
        trace.record(new ChangeEntry(TraceEntity.ATTACHMENT, null, fileName, TraceEntity.AUDIT, auditId, auditId,
                TraceAction.DELETE, "Eliminó el adjunto «" + fileName + "»",
                Changes.create().track("file", "Fichero", fileName, null).list()));
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanDelete(AttachmentEntityType type, Long entityId) {
        Long auditId = auditIdOf(type, entityId);
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
