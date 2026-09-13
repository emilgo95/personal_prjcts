package com.agriaudit.service.document;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.domain.DocumentTemplate;
import com.agriaudit.domain.GeneratedDocument;
import com.agriaudit.domain.enums.DocumentFormat;
import com.agriaudit.domain.enums.DocumentType;
import com.agriaudit.repo.DocumentTemplateRepository;
import com.agriaudit.repo.GeneratedDocumentRepository;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.web.dto.DocumentDtos.DocumentOptionDto;
import com.agriaudit.web.dto.DocumentDtos.GeneratedDocumentDto;
import com.agriaudit.storage.BlobStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentGenerationService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Map<DocumentType, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(DocumentType.AUDIT_PLAN, "Plan de auditoría");
        LABELS.put(DocumentType.CHECKLIST, "Checklist de evaluación");
        LABELS.put(DocumentType.NONCONFORMITY_REPORT, "Informe de no conformidades");
        LABELS.put(DocumentType.CAPA_REPORT, "Plan de acciones correctivas (CAPA)");
        LABELS.put(DocumentType.AUDIT_REPORT, "Informe de auditoría");
        LABELS.put(DocumentType.COMPLIANCE_SUMMARY, "Resumen de cumplimiento");
        LABELS.put(DocumentType.GRASP_ASSESSMENT, "Informe de evaluación GRASP");
        LABELS.put(DocumentType.SMETA_REPORT, "Informe SMETA");
    }

    private final DocumentTemplateRepository templates;
    private final GeneratedDocumentRepository generatedDocuments;
    private final BlobStore storage;
    private final DocumentModelAssembler assembler;
    private final AuditLookup auditService;
    private final List<DocumentRenderer> renderers;

    public DocumentGenerationService(DocumentTemplateRepository templates,
                                     GeneratedDocumentRepository generatedDocuments,
                                     BlobStore storage,
                                     DocumentModelAssembler assembler,
                                     AuditLookup auditService,
                                     List<DocumentRenderer> renderers) {
        this.templates = templates;
        this.generatedDocuments = generatedDocuments;
        this.storage = storage;
        this.assembler = assembler;
        this.auditService = auditService;
        this.renderers = renderers;
    }

    @Transactional
    public GeneratedDocumentDto generate(Long auditId, String documentTypeRaw, String formatRaw) {
        AuditRef audit = auditService.requireAudit(auditId);
        DocumentType type = parseType(documentTypeRaw);
        DocumentFormat format = parseFormat(formatRaw);

        DocumentTemplate template = templates
                .findByStandardCodeAndDocumentTypeAndFormat(audit.standardCode(), type, format)
                .or(() -> templates.findByStandardCodeIsNullAndDocumentTypeAndFormat(type, format))
                .orElseThrow(() -> new BadRequestException("No hay plantilla para " + type + " en formato " + format));

        DocumentRenderer renderer = renderers.stream()
                .filter(r -> r.supports(template.getEngine()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Motor de plantilla no soportado: " + template.getEngine()));

        DocumentModel model = assembler.assemble(auditId, LABELS.getOrDefault(type, type.name()));
        byte[] bytes = renderer.render(template, model);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("El documento generado está vacío");
        }

        String fileName = buildFileName(type, format, audit.reference());
        String storageKey = storage.store(bytes, fileName, "documents");

        GeneratedDocument doc = new GeneratedDocument();
        doc.setAuditId(auditId);
        doc.setOrganizationId(SecurityUtils.currentOrganizationId());
        doc.setDocumentType(type);
        doc.setStandardCode(audit.standardCode());
        doc.setFormat(format);
        doc.setFileName(fileName);
        doc.setStorageKey(storageKey);
        doc.setSizeBytes((long) bytes.length);
        doc.setSha256(sha256(bytes));
        doc.setGeneratedBy(SecurityUtils.currentUsername());
        doc.setParams("{\"documentType\":\"" + type + "\",\"format\":\"" + format + "\"}");
        return toDto(generatedDocuments.save(doc), audit.reference());
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentDto> listForAudit(Long auditId) {
        AuditRef audit = auditService.requireAudit(auditId);
        return generatedDocuments.findByAuditIdOrderByGeneratedAtDesc(auditId).stream()
                .map(d -> toDto(d, audit.reference())).toList();
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentDto> listForOrganization() {
        return generatedDocuments.findByOrganizationIdOrderByGeneratedAtDesc(SecurityUtils.currentOrganizationId())
                .stream().map(d -> toDto(d, null)).toList();
    }

    @Transactional(readOnly = true)
    public DownloadedDocument download(Long documentId) {
        GeneratedDocument doc = generatedDocuments
                .findByIdAndOrganizationId(documentId, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Documento no encontrado: " + documentId));
        byte[] data = storage.load(doc.getStorageKey());
        return new DownloadedDocument(doc.getFileName(), contentType(doc.getFormat()), data);
    }

    @Transactional(readOnly = true)
    public List<DocumentOptionDto> optionsForAudit(Long auditId) {
        AuditRef audit = auditService.requireAudit(auditId);
        String standardCode = audit.standardCode();
        Map<DocumentType, Set<String>> byType = new LinkedHashMap<>();
        for (DocumentTemplate t : templates.findByActiveTrue()) {
            if (t.getStandardCode() == null || t.getStandardCode().equals(standardCode)) {
                byType.computeIfAbsent(t.getDocumentType(), k -> new LinkedHashSet<>()).add(t.getFormat().name());
            }
        }
        return byType.entrySet().stream()
                .map(e -> new DocumentOptionDto(e.getKey().name(),
                        LABELS.getOrDefault(e.getKey(), e.getKey().name()),
                        e.getValue().stream().sorted().toList()))
                .sorted(Comparator.comparing(DocumentOptionDto::label))
                .collect(Collectors.toList());
    }

    // ---- helpers ----

    private DocumentType parseType(String raw) {
        try {
            return DocumentType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de documento no válido: " + raw);
        }
    }

    private DocumentFormat parseFormat(String raw) {
        try {
            return DocumentFormat.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Formato no válido: " + raw);
        }
    }

    private String buildFileName(DocumentType type, DocumentFormat format, String reference) {
        String slug = LABELS.getOrDefault(type, type.name())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[áàä]", "a").replaceAll("[éèë]", "e").replaceAll("[íìï]", "i")
                .replaceAll("[óòö]", "o").replaceAll("[úùü]", "u").replace("ñ", "n")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug + "_" + reference + "_" + FILE_STAMP.format(LocalDate.now()) + "." + format.name().toLowerCase(Locale.ROOT);
    }

    private String contentType(DocumentFormat format) {
        return switch (format) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        };
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private GeneratedDocumentDto toDto(GeneratedDocument d, String auditReference) {
        return new GeneratedDocumentDto(
                d.getId(), d.getAuditId(), auditReference,
                d.getDocumentType().name(), LABELS.getOrDefault(d.getDocumentType(), d.getDocumentType().name()),
                d.getStandardCode(), d.getFormat().name(), d.getFileName(),
                d.getSizeBytes(), d.getSha256(), d.getGeneratedBy(), d.getGeneratedAt());
    }

    public record DownloadedDocument(String fileName, String contentType, byte[] data) {
    }
}
