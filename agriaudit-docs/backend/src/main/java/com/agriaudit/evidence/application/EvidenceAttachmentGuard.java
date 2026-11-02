package com.agriaudit.evidence.application;

import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecordRepository;
import com.agriaudit.storage.AttachmentDeletionGuard;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deja constancia en la traza cuando se borra un documento de un registro de evidencia. */
@Component
class EvidenceAttachmentGuard implements AttachmentDeletionGuard {

    private final EvidenceRecordRepository records;
    private final ChangeRecorder trace;

    EvidenceAttachmentGuard(EvidenceRecordRepository records, ChangeRecorder trace) {
        this.records = records;
        this.trace = trace;
    }

    @Override
    public void checkCanDelete(AttachmentEntityType type, Long entityId) {
        // Los documentos de un registro se pueden borrar siempre.
    }

    @Override
    @Transactional
    public void afterDelete(AttachmentEntityType type, Long entityId, String fileName) {
        if (type != AttachmentEntityType.EVIDENCE_RECORD) {
            return;
        }
        records.findById(entityId).ifPresent(r -> trace.record(new ChangeEntry(TraceEntity.ATTACHMENT, null, fileName,
                TraceEntity.EVIDENCE_RECORD, r.getId(), null, TraceAction.DELETE,
                "Eliminó el documento «" + fileName + "» de la evidencia «" + r.getTitle() + "»",
                Changes.create().track("file", "Fichero", fileName, null).list())));
    }
}
