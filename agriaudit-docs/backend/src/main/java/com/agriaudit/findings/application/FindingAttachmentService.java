package com.agriaudit.findings.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.storage.AttachmentStore.NewAttachment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Adjuntos de evidencia de una no conformidad: comprueba que la NC pertenece a una auditoría de la
 * organización del usuario y delega el almacenamiento en {@link AttachmentStore}.
 */
@Service
@Transactional
public class FindingAttachmentService {

    private final AttachmentStore attachments;
    private final FindingService findingService;
    private final ChangeRecorder trace;

    public FindingAttachmentService(AttachmentStore attachments, FindingService findingService,
                                    ChangeRecorder trace) {
        this.attachments = attachments;
        this.findingService = findingService;
        this.trace = trace;
    }

    public AttachmentDto uploadForFinding(Long auditId, Long findingId, MultipartFile file, boolean personalData) {
        var finding = findingService.requireEditableFinding(auditId, findingId);
        Long id = finding.getId();
        if (file == null) {
            throw new BadRequestException("El fichero está vacío");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fichero subido", e);
        }
        AttachmentDto stored = attachments.store(new NewAttachment(SecurityUtils.currentOrganizationId(),
                SecurityUtils.currentUsername(), AttachmentEntityType.FINDING, id, file.getOriginalFilename(),
                file.getContentType(), bytes, personalData));
        trace.record(new ChangeEntry(TraceEntity.ATTACHMENT, stored.id(), stored.fileName(), TraceEntity.FINDING,
                id, finding.getAuditId(), TraceAction.UPLOAD, "Subió el adjunto «" + stored.fileName()
                + "» a la no conformidad " + finding.getReference(),
                Changes.create().initial("file", "Fichero", stored.fileName())
                        .initial("containsPersonalData", "Contiene datos personales", personalData ? "Sí" : null).list()));
        return stored;
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForFinding(Long auditId, Long findingId) {
        findingService.requireOwnedFinding(auditId, findingId);
        return attachments.byEntity(AttachmentEntityType.FINDING, findingId);
    }
}
