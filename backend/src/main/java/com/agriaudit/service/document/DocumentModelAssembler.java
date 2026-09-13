package com.agriaudit.service.document;

import com.agriaudit.domain.Audit;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.identity.OrganizationDirectory;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.service.AuditService;
import com.agriaudit.catalog.CatalogService;
import com.agriaudit.service.FindingService;
import com.agriaudit.service.ScoringService;
import com.agriaudit.service.document.DocumentModel.EvidenceRef;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.web.dto.AuditDtos.AuditDetailDto;
import com.agriaudit.web.dto.AuditDtos.AuditItemResultDto;
import com.agriaudit.catalog.CatalogDtos.StandardDetailDto;
import com.agriaudit.web.dto.FindingDtos.FindingDto;
import com.agriaudit.web.dto.ScoreDtos.ScoreResult;
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

    private final AuditService auditService;
    private final CatalogService catalogService;
    private final FindingService findingService;
    private final ScoringService scoringService;
    private final OrganizationDirectory organizations;
    private final AttachmentStore attachmentLookup;

    public DocumentModelAssembler(AuditService auditService, CatalogService catalogService,
                                  FindingService findingService, ScoringService scoringService,
                                  OrganizationDirectory organizations, AttachmentStore attachmentLookup) {
        this.auditService = auditService;
        this.catalogService = catalogService;
        this.findingService = findingService;
        this.scoringService = scoringService;
        this.organizations = organizations;
        this.attachmentLookup = attachmentLookup;
    }

    @Transactional(readOnly = true)
    public DocumentModel assemble(Long auditId, String documentTitle) {
        Audit audit = auditService.requireAudit(auditId);
        AuditDetailDto detail = auditService.get(auditId);
        StandardDetailDto standard = catalogService.getStandard(audit.getStandard().getCode());
        List<AuditItemResultDto> results = auditService.getResults(auditId);
        List<FindingDto> findings = findingService.listByAudit(auditId);
        ScoreResult score = scoringService.score(audit);

        String orgName = organizations.organizationName(SecurityUtils.currentOrganizationId())
                .orElse("Organización");

        var resultsByCp = results.stream().collect(Collectors.toMap(
                AuditItemResultDto::controlPointId, Function.identity(), (a, b) -> a));

        Map<Long, List<EvidenceRef>> evidenceByControlPoint = new LinkedHashMap<>();
        for (AuditItemResultDto r : results) {
            List<EvidenceRef> refs = toEvidence(r.attachments());
            if (!refs.isEmpty()) {
                evidenceByControlPoint.put(r.controlPointId(), refs);
            }
        }
        Map<Long, List<EvidenceRef>> evidenceByFinding = new LinkedHashMap<>();
        for (FindingDto f : findings) {
            List<EvidenceRef> refs = toEvidence(f.attachments());
            if (!refs.isEmpty()) {
                evidenceByFinding.put(f.id(), refs);
            }
        }
        List<EvidenceRef> auditEvidence =
                toEvidence(attachmentLookup.byEntity(AttachmentEntityType.AUDIT, auditId));

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

    private List<EvidenceRef> toEvidence(List<AttachmentDto> attachments) {
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
                    && images < MAX_IMAGES_PER_ENTITY;
            if (embeddable) {
                dataUri = loadDataUri(a);
                if (dataUri != null) {
                    images++;
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
