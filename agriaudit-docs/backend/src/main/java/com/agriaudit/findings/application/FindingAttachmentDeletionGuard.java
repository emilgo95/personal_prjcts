package com.agriaudit.findings.application;

import com.agriaudit.audit.AuditLookup;
import com.agriaudit.common.ConflictException;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import com.agriaudit.storage.AttachmentDeletionGuard;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** No deja borrar evidencias de una no conformidad cuya auditoría está enviada o cerrada. */
@Component
class FindingAttachmentDeletionGuard implements AttachmentDeletionGuard {

    private final FindingRepository findings;
    private final AuditLookup audits;
    private final ChangeRecorder trace;

    FindingAttachmentDeletionGuard(FindingRepository findings, AuditLookup audits, ChangeRecorder trace) {
        this.findings = findings;
        this.audits = audits;
        this.trace = trace;
    }

    @Override
    @Transactional
    public void afterDelete(AttachmentEntityType type, Long entityId, String fileName) {
        if (type != AttachmentEntityType.FINDING) {
            return;
        }
        findings.findById(entityId).ifPresent(f -> trace.record(new ChangeEntry(TraceEntity.ATTACHMENT, null,
                fileName, TraceEntity.FINDING, f.getId(), f.getAuditId(), TraceAction.DELETE,
                "Eliminó el adjunto «" + fileName + "» de la no conformidad " + f.getReference(),
                Changes.create().track("file", "Fichero", fileName, null).list())));
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
