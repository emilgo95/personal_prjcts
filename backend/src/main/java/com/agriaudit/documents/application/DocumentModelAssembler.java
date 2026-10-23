package com.agriaudit.documents.application;

import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.identity.OrganizationDirectory;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.catalog.CatalogService;
import com.agriaudit.findings.FindingLookup;
import com.agriaudit.documents.application.DocumentModel.EvidenceRef;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.audit.AuditDtos.AuditDetailDto;
import com.agriaudit.audit.AuditDtos.AuditItemResultDto;
import com.agriaudit.catalog.CatalogDtos.StandardDetailDto;
import com.agriaudit.findings.FindingDtos.FindingDto;
import com.agriaudit.audit.ScoreDtos.ScoreResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DocumentModelAssembler {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "ES")).withZone(ZoneId.systemDefault());

    /** Formatos de imagen que openhtmltopdf y POI embeben con fiabilidad. */
    private static final Set<String> EMBEDDABLE = Set.of("image/jpeg", "image/png");
    private static final long MAX_EMBED_BYTES = 4L * 1024 * 1024;
    private static final int MAX_IMAGES_PER_ENTITY = 6;
    /** Tope de imágenes incrustadas en un mismo documento, para no agotar la memoria con auditorías muy grandes. */
    private static final int MAX_IMAGES_PER_DOCUMENT = 60;
    private static final long MAX_EMBED_BYTES_PER_DOCUMENT = 48L * 1024 * 1024;

    private final AuditLookup auditService;
    private final CatalogService catalogService;
    private final FindingLookup findingService;
    private final OrganizationDirectory organizations;
    private final AttachmentStore attachmentLookup;

    public DocumentModelAssembler(AuditLookup auditService, CatalogService catalogService,
                                  FindingLookup findingService,
                                  OrganizationDirectory organizations, AttachmentStore attachmentLookup) {
        this.auditService = auditService;
        this.catalogService = catalogService;
        this.findingService = findingService;
        this.organizations = organizations;
        this.attachmentLookup = attachmentLookup;
    }

    @Transactional(readOnly = true)
    public DocumentModel assemble(Long auditId, String documentTitle) {
        AuditRef audit = auditService.requireAudit(auditId);
        AuditDetailDto detail = auditService.get(auditId);
        StandardDetailDto standard = catalogService.getStandard(audit.standardCode());
        List<AuditItemResultDto> results = auditService.getResults(auditId);
        List<FindingDto> findings = findingService.listByAudit(auditId);
        ScoreResult score = auditService.score(auditId);

        String orgName = organizations.organizationName(SecurityUtils.currentOrganizationId())
                .orElse("Organización");

        var resultsByCp = results.stream().collect(Collectors.toMap(
                AuditItemResultDto::controlPointId, Function.identity(), (a, b) -> a));

        EmbedBudget budget = new EmbedBudget();
        Map<Long, List<EvidenceRef>> evidenceByControlPoint = new LinkedHashMap<>();
        for (AuditItemResultDto r : results) {
            List<EvidenceRef> refs = toEvidence(r.attachments(), budget);
            if (!refs.isEmpty()) {
                evidenceByControlPoint.put(r.controlPointId(), refs);
            }
        }
        Map<Long, List<EvidenceRef>> evidenceByFinding = new LinkedHashMap<>();
        for (FindingDto f : findings) {
            List<EvidenceRef> refs = toEvidence(f.attachments(), budget);
            if (!refs.isEmpty()) {
                evidenceByFinding.put(f.id(), refs);
            }
        }
        List<EvidenceRef> auditEvidence =
                toEvidence(attachmentLookup.byEntity(AttachmentEntityType.AUDIT, auditId), budget);

        return new DocumentModel(
                orgName,
                SecurityUtils.currentUsername(),
                STAMP.format(java.time.Instant.now()),
                documentTitle,
                detail,
                detail.farm(),
                standard,
                standard.chapters(),
                results,
                resultsByCp,
                findings,
                score,
                evidenceByControlPoint,
                evidenceByFinding,
                auditEvidence,
                DocumentModel.DEFAULT_DISCLAIMER);
    }

    /** Imágenes y bytes ya incrustados en el documento en curso. */
    private static final class EmbedBudget {
        int images;
        long bytes;

        boolean allows(long size) {
            return images < MAX_IMAGES_PER_DOCUMENT && bytes + size <= MAX_EMBED_BYTES_PER_DOCUMENT;
        }

        void spend(long size) {
            images++;
            bytes += size;
        }
    }

    private List<EvidenceRef> toEvidence(List<AttachmentDto> attachments, EmbedBudget budget) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        int images = 0;
        List<EvidenceRef> refs = new java.util.ArrayList<>();
        for (AttachmentDto a : attachments) {
            String dataUri = null;
            boolean embeddable = a.image()
                    && a.contentType() != null && EMBEDDABLE.contains(a.contentType().toLowerCase())
                    && a.sizeBytes() != null && a.sizeBytes() <= MAX_EMBED_BYTES
                    && images < MAX_IMAGES_PER_ENTITY
                    && budget.allows(a.sizeBytes());
            if (embeddable) {
                dataUri = loadDataUri(a);
                if (dataUri != null) {
                    images++;
                    budget.spend(a.sizeBytes());
                }
            }
            refs.add(new EvidenceRef(a.id(), a.fileName(), a.contentType(), a.sizeBytes(),
                    a.uploadedBy(), a.image(), dataUri));
        }
        return refs;
    }

    private String loadDataUri(AttachmentDto a) {
        try {
            return "data:" + a.contentType().toLowerCase() + ";base64,"
                    + Base64.getEncoder().encodeToString(
                            attachmentLookup.content(SecurityUtils.currentOrganizationId(), a.id()));
        } catch (Exception e) {
            return null;
        }
    }
}
