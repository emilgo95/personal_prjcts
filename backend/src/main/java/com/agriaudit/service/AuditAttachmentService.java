package com.agriaudit.service;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.domain.*;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.domain.enums.ComplianceResult;
import com.agriaudit.repo.*;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.storage.AttachmentStore.NewAttachment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.catalog.CatalogLookup;

@Service
@Transactional
public class AuditAttachmentService {

    private final AttachmentStore attachments;
    private final AuditService auditService;
    private final FindingRepository findings;
    private final AuditItemResultRepository results;
    private final CatalogLookup catalog;

    public AuditAttachmentService(AttachmentStore attachments, AuditService auditService,
                                  FindingRepository findings, AuditItemResultRepository results,
                                  CatalogLookup catalog) {
        this.attachments = attachments;
        this.auditService = auditService;
        this.findings = findings;
        this.results = results;
        this.catalog = catalog;
    }

    // ---- subir ----

    public AttachmentDto uploadForAudit(Long auditId, MultipartFile file) {
        Audit audit = auditService.requireAudit(auditId);
        return store(file, AttachmentEntityType.AUDIT, audit.getId());
    }

    public AttachmentDto uploadForControlPoint(Long auditId, Long controlPointId, MultipartFile file) {
        AuditItemResult result = getOrCreateResult(auditId, controlPointId);
        return store(file, AttachmentEntityType.AUDIT_ITEM_RESULT, result.getId());
    }

    public AttachmentDto uploadForFinding(Long auditId, Long findingId, MultipartFile file) {
        Finding finding = requireFinding(auditId, findingId);
        return store(file, AttachmentEntityType.FINDING, finding.getId());
    }

    // ---- listar ----

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForAudit(Long auditId) {
        auditService.requireAudit(auditId);
        return attachments.byEntity(AttachmentEntityType.AUDIT, auditId);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForControlPoint(Long auditId, Long controlPointId) {
        auditService.requireAudit(auditId);
        return results.findByAuditIdAndControlPointId(auditId, controlPointId)
                .map(r -> attachments.byEntity(AttachmentEntityType.AUDIT_ITEM_RESULT, r.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForFinding(Long auditId, Long findingId) {
        requireFinding(auditId, findingId);
        return attachments.byEntity(AttachmentEntityType.FINDING, findingId);
    }

    // ---- helpers ----

    private AttachmentDto store(MultipartFile file, AttachmentEntityType type, Long entityId) {
        if (file == null) {
            throw new BadRequestException("El fichero está vacío");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fichero subido", e);
        }
        return attachments.store(new NewAttachment(SecurityUtils.currentOrganizationId(),
                SecurityUtils.currentUsername(), type, entityId, file.getOriginalFilename(),
                file.getContentType(), bytes));
    }

    private Finding requireFinding(Long auditId, Long findingId) {
        auditService.requireAudit(auditId);
        return findings.findByIdAndAuditId(findingId, auditId)
                .orElseThrow(() -> new NotFoundException("Hallazgo no encontrado: " + findingId));
    }

    private AuditItemResult getOrCreateResult(Long auditId, Long controlPointId) {
        Audit audit = auditService.requireAudit(auditId);
        return results.findByAuditIdAndControlPointId(auditId, controlPointId).orElseGet(() -> {
            ControlPoint cp = catalog.findControlPoint(controlPointId)
                    .orElseThrow(() -> new NotFoundException("Punto de control no encontrado: " + controlPointId));
            if (!cp.getChapter().getStandard().getId().equals(audit.getStandard().getId())) {
                throw new BadRequestException("El punto de control no pertenece al estándar de la auditoría");
            }
            AuditItemResult r = new AuditItemResult();
            r.setAuditId(auditId);
            r.setControlPoint(cp);
            r.setCompliance(ComplianceResult.NOT_ASSESSED);
            return results.save(r);
        });
    }
}
