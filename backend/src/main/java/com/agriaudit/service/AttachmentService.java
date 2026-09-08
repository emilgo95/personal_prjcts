package com.agriaudit.service;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.domain.*;
import com.agriaudit.domain.enums.AttachmentEntityType;
import com.agriaudit.domain.enums.ComplianceResult;
import com.agriaudit.repo.*;
import com.agriaudit.security.SecurityUtils;
import com.agriaudit.service.document.DocumentStorageService;
import com.agriaudit.web.dto.AttachmentDtos.AttachmentDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class AttachmentService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif",
            "application/pdf");
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private final AttachmentRepository attachments;
    private final AttachmentLookup lookup;
    private final DocumentStorageService storage;
    private final AuditService auditService;
    private final FindingRepository findings;
    private final AuditItemResultRepository results;
    private final ControlPointRepository controlPoints;

    public AttachmentService(AttachmentRepository attachments, AttachmentLookup lookup,
                             DocumentStorageService storage, AuditService auditService,
                             FindingRepository findings, AuditItemResultRepository results,
                             ControlPointRepository controlPoints) {
        this.attachments = attachments;
        this.lookup = lookup;
        this.storage = storage;
        this.auditService = auditService;
        this.findings = findings;
        this.results = results;
        this.controlPoints = controlPoints;
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
        return lookup.byEntity(AttachmentEntityType.AUDIT, auditId);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForControlPoint(Long auditId, Long controlPointId) {
        auditService.requireAudit(auditId);
        return results.findByAuditIdAndControlPointId(auditId, controlPointId)
                .map(r -> lookup.byEntity(AttachmentEntityType.AUDIT_ITEM_RESULT, r.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForFinding(Long auditId, Long findingId) {
        requireFinding(auditId, findingId);
        return lookup.byEntity(AttachmentEntityType.FINDING, findingId);
    }

    // ---- descargar / borrar ----

    @Transactional(readOnly = true)
    public DownloadedFile download(Long attachmentId) {
        Attachment a = require(attachmentId);
        return new DownloadedFile(a.getFileName(),
                a.getContentType() == null ? "application/octet-stream" : a.getContentType(),
                storage.load(a.getStorageKey()));
    }

    public void delete(Long attachmentId) {
        Attachment a = require(attachmentId);
        storage.delete(a.getStorageKey());
        attachments.delete(a);
    }

    // ---- helpers ----

    private AttachmentDto store(MultipartFile file, AttachmentEntityType type, Long entityId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El fichero está vacío");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("El fichero supera el tamaño máximo de 25 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String original = file.getOriginalFilename() == null ? "evidencia" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fichero subido", e);
        }

        String resolved = resolveContentType(contentType, original, bytes);
        if (resolved == null) {
            throw new BadRequestException("Tipo de fichero no permitido. Se aceptan imágenes (JPG, PNG, GIF, WEBP, HEIC) y PDF.");
        }

        String key = storage.store(bytes, original, "attachments");

        Attachment a = new Attachment();
        a.setOrganizationId(SecurityUtils.currentOrganizationId());
        a.setEntityType(type.name());
        a.setEntityId(entityId);
        a.setFileName(original);
        a.setContentType(resolved);
        a.setSizeBytes((long) bytes.length);
        a.setStorageKey(key);
        a.setUploadedBy(SecurityUtils.currentUsername());
        return AttachmentLookup.toDto(attachments.save(a));
    }

    /**
     * Determina un tipo de contenido válido a partir del que declara el cliente, la extensión
     * del fichero y los "magic bytes". Devuelve {@code null} si no es imagen ni PDF.
     */
    private String resolveContentType(String declared, String fileName, byte[] bytes) {
        if (declared != null && ALLOWED_TYPES.contains(declared)) {
            return declared;
        }
        String byExt = fromExtension(fileName);
        if (byExt != null) {
            return byExt;
        }
        return fromMagic(bytes);
    }

    private String fromExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return switch (fileName.substring(dot + 1).toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "pdf" -> "application/pdf";
            default -> null;
        };
    }

    private String fromMagic(byte[] b) {
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            return "image/heic";
        }
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        return null;
    }

    private Attachment require(Long id) {
        return attachments.findByIdAndOrganizationId(id, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Adjunto no encontrado: " + id));
    }

    private Finding requireFinding(Long auditId, Long findingId) {
        auditService.requireAudit(auditId);
        return findings.findByIdAndAuditId(findingId, auditId)
                .orElseThrow(() -> new NotFoundException("Hallazgo no encontrado: " + findingId));
    }

    private AuditItemResult getOrCreateResult(Long auditId, Long controlPointId) {
        Audit audit = auditService.requireAudit(auditId);
        return results.findByAuditIdAndControlPointId(auditId, controlPointId).orElseGet(() -> {
            ControlPoint cp = controlPoints.findById(controlPointId)
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

    public record DownloadedFile(String fileName, String contentType, byte[] data) {
    }
}
