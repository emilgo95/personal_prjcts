package com.agriaudit.audit.application;

import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.domain.AuditItemResult;
import com.agriaudit.audit.domain.ComplianceResult;
import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
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
 * Adjuntos de evidencia de una auditoría y de sus puntos de control: comprueba que la auditoría es de
 * la organización del usuario y delega el almacenamiento en {@link AttachmentStore}.
 */
@Service
@Transactional
public class AuditAttachmentService {

    private final AttachmentStore attachments;
    private final AuditService auditService;
    private final AuditItemResultRepository results;
    private final CatalogLookup catalog;
    private final ChangeRecorder trace;

    public AuditAttachmentService(AttachmentStore attachments, AuditService auditService,
                                  AuditItemResultRepository results, CatalogLookup catalog, ChangeRecorder trace) {
        this.trace = trace;
        this.attachments = attachments;
        this.auditService = auditService;
        this.results = results;
        this.catalog = catalog;
    }

    public AttachmentDto uploadForAudit(Long auditId, MultipartFile file) {
        Audit audit = auditService.requireEditableAuditEntity(auditId);
        return store(file, AttachmentEntityType.AUDIT, audit.getId(), audit.getId(), "la auditoría");
    }

    public AttachmentDto uploadForControlPoint(Long auditId, Long controlPointId, MultipartFile file) {
        AuditItemResult result = getOrCreateResult(auditId, controlPointId);
        return store(file, AttachmentEntityType.AUDIT_ITEM_RESULT, result.getId(), auditId,
                "un punto de control");
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForAudit(Long auditId) {
        auditService.requireAuditEntity(auditId);
        return attachments.byEntity(AttachmentEntityType.AUDIT, auditId);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForControlPoint(Long auditId, Long controlPointId) {
        auditService.requireAuditEntity(auditId);
        return results.findByAuditIdAndControlPointId(auditId, controlPointId)
                .map(r -> attachments.byEntity(AttachmentEntityType.AUDIT_ITEM_RESULT, r.getId()))
                .orElse(List.of());
    }

    private AttachmentDto store(MultipartFile file, AttachmentEntityType type, Long entityId, Long auditId,
                                String target) {
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
                SecurityUtils.currentUsername(), type, entityId, file.getOriginalFilename(),
                file.getContentType(), bytes));
        trace.record(new ChangeEntry(TraceEntity.ATTACHMENT, stored.id(), stored.fileName(), TraceEntity.AUDIT,
                auditId, auditId, TraceAction.UPLOAD, "Subió el adjunto «" + stored.fileName() + "» a " + target,
                Changes.create().initial("file", "Fichero", stored.fileName()).list()));
        return stored;
    }

    private AuditItemResult getOrCreateResult(Long auditId, Long controlPointId) {
        Audit audit = auditService.requireEditableAuditEntity(auditId);
        return results.findByAuditIdAndControlPointId(auditId, controlPointId).orElseGet(() -> {
            ControlPointView cp = catalog.findControlPoint(controlPointId)
                    .orElseThrow(() -> new NotFoundException("Punto de control no encontrado: " + controlPointId));
            if (!cp.belongsTo(audit.getStandardId())) {
                throw new BadRequestException("El punto de control no pertenece al estándar de la auditoría");
            }
            AuditItemResult r = new AuditItemResult();
            r.setAuditId(auditId);
            r.setControlPointId(cp.id());
            r.setCompliance(ComplianceResult.NOT_ASSESSED);
            return results.save(r);
        });
    }
}
