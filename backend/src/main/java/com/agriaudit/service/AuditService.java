package com.agriaudit.service;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.domain.*;
import com.agriaudit.domain.enums.AttachmentEntityType;
import com.agriaudit.domain.enums.AuditStatus;
import com.agriaudit.domain.enums.AuditType;
import com.agriaudit.domain.enums.ComplianceResult;
import com.agriaudit.repo.*;
import com.agriaudit.security.SecurityUtils;
import com.agriaudit.web.dto.AuditDtos.*;
import com.agriaudit.web.dto.FarmDtos.FarmDto;
import com.agriaudit.web.dto.ScoreDtos.ScoreResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class AuditService {

    private final AuditRepository audits;
    private final FarmRepository farms;
    private final AuditStandardRepository standards;
    private final ControlPointRepository controlPoints;
    private final AuditItemResultRepository results;
    private final FindingRepository findings;
    private final ScoringService scoringService;
    private final AttachmentLookup attachmentLookup;
    private final ReferenceSequenceRepository referenceSequences;

    public AuditService(AuditRepository audits, FarmRepository farms, AuditStandardRepository standards,
                        ControlPointRepository controlPoints, AuditItemResultRepository results,
                        FindingRepository findings, ScoringService scoringService,
                        AttachmentLookup attachmentLookup, ReferenceSequenceRepository referenceSequences) {
        this.audits = audits;
        this.farms = farms;
        this.standards = standards;
        this.controlPoints = controlPoints;
        this.results = results;
        this.findings = findings;
        this.scoringService = scoringService;
        this.attachmentLookup = attachmentLookup;
        this.referenceSequences = referenceSequences;
    }

    @Transactional(readOnly = true)
    public List<AuditSummaryDto> list() {
        return audits.findByOrganizationIdOrderByCreatedAtDesc(SecurityUtils.currentOrganizationId())
                .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public AuditDetailDto get(Long id) {
        return toDetail(requireAudit(id));
    }

    public AuditDetailDto create(AuditRequest request) {
        Long orgId = SecurityUtils.currentOrganizationId();
        Farm farm = farms.findByIdAndOrganizationId(request.farmId(), orgId)
                .orElseThrow(() -> new NotFoundException("Explotación no encontrada: " + request.farmId()));
        AuditStandard standard = standards.findByCode(request.standardCode())
                .orElseThrow(() -> new NotFoundException("Estándar no encontrado: " + request.standardCode()));

        Audit audit = new Audit();
        audit.setOrganizationId(orgId);
        audit.setFarm(farm);
        audit.setStandard(standard);
        audit.setReference(nextReference(orgId));
        audit.setAuditType(parseType(request.auditType()));
        audit.setStatus(AuditStatus.DRAFT);
        audit.setCreatedByUserId(SecurityUtils.currentUserId());
        applyRequest(audit, request, farm);
        return toDetail(audits.save(audit));
    }

    public AuditDetailDto update(Long id, AuditRequest request) {
        Audit audit = requireAudit(id);
        if (!audit.getStandard().getCode().equals(request.standardCode())) {
            throw new BadRequestException("No se puede cambiar el estándar de una auditoría existente");
        }
        Farm farm = audit.getFarm();
        if (!farm.getId().equals(request.farmId())) {
            farm = farms.findByIdAndOrganizationId(request.farmId(), SecurityUtils.currentOrganizationId())
                    .orElseThrow(() -> new NotFoundException("Explotación no encontrada: " + request.farmId()));
            audit.setFarm(farm);
        }
        audit.setAuditType(parseType(request.auditType()));
        applyRequest(audit, request, farm);
        return toDetail(audits.save(audit));
    }

    public AuditDetailDto transition(Long id, String status) {
        Audit audit = requireAudit(id);
        AuditStatus target;
        try {
            target = AuditStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Estado no válido: " + status);
        }
        audit.setStatus(target);
        return toDetail(audits.save(audit));
    }

    // ---- resultados del checklist ----

    @Transactional(readOnly = true)
    public List<AuditItemResultDto> getResults(Long id) {
        Audit audit = requireAudit(id);
        Map<Long, AuditItemResult> existing = new HashMap<>();
        for (AuditItemResult r : results.findByAuditId(audit.getId())) {
            existing.put(r.getControlPoint().getId(), r);
        }
        List<AuditItemResultDto> out = new ArrayList<>();
        for (ControlPoint cp : controlPoints.findByStandardCode(audit.getStandard().getCode())) {
            AuditItemResult r = existing.get(cp.getId());
            out.add(new AuditItemResultDto(
                    r == null ? null : r.getId(),
                    cp.getId(), cp.getRef(), cp.getTitle(), cp.getDescription(), cp.getGuidance(),
                    cp.getLevel().name(), cp.getApplicableScopes(),
                    cp.getChapter().getCode(), cp.getChapter().getTitle(),
                    r == null ? ComplianceResult.NOT_ASSESSED.name() : r.getCompliance().name(),
                    r == null ? null : r.getEvidenceNotes(),
                    r == null ? null : r.getAuditorComment(),
                    r == null ? null : r.getAssessedAt(),
                    r == null ? null : r.getAssessedBy(),
                    r == null ? List.of()
                            : attachmentLookup.byEntity(AttachmentEntityType.AUDIT_ITEM_RESULT, r.getId())));
        }
        return out;
    }

    public List<AuditItemResultDto> updateResults(Long id, ResultsUpdateRequest request) {
        Audit audit = requireAudit(id);
        Map<Long, AuditItemResult> existing = new HashMap<>();
        for (AuditItemResult r : results.findByAuditId(audit.getId())) {
            existing.put(r.getControlPoint().getId(), r);
        }
        String user = SecurityUtils.currentUsername();
        for (ResultUpdate u : request.results()) {
            ComplianceResult compliance;
            try {
                compliance = ComplianceResult.valueOf(u.compliance());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Valor de cumplimiento no válido: " + u.compliance());
            }
            AuditItemResult r = existing.get(u.controlPointId());
            if (r == null) {
                ControlPoint cp = controlPoints.findById(u.controlPointId())
                        .orElseThrow(() -> new NotFoundException("Punto de control no encontrado: " + u.controlPointId()));
                if (!cp.getChapter().getStandard().getId().equals(audit.getStandard().getId())) {
                    throw new BadRequestException("El punto de control no pertenece al estándar de la auditoría");
                }
                r = new AuditItemResult();
                r.setAuditId(audit.getId());
                r.setControlPoint(cp);
                existing.put(cp.getId(), r);
            }
            r.setCompliance(compliance);
            r.setEvidenceNotes(u.evidenceNotes());
            r.setAuditorComment(u.auditorComment());
            r.setAssessedAt(Instant.now());
            r.setAssessedBy(user);
            results.save(r);
        }
        if (audit.getStatus() == AuditStatus.DRAFT) {
            audit.setStatus(AuditStatus.IN_PROGRESS);
            audits.save(audit);
        }
        return getResults(id);
    }

    @Transactional(readOnly = true)
    public ScoreResult score(Long id) {
        return scoringService.score(requireAudit(id));
    }

    // ---- helpers ----

    @Transactional(readOnly = true)
    public Audit requireAudit(Long id) {
        return audits.findByIdAndOrganizationId(id, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Auditoría no encontrada: " + id));
    }

    private AuditType parseType(String value) {
        try {
            return AuditType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de auditoría no válido: " + value);
        }
    }

    private void applyRequest(Audit audit, AuditRequest r, Farm farm) {
        audit.setScopeDescription(r.scopeDescription());
        audit.setPlannedStartDate(r.plannedStartDate());
        audit.setPlannedEndDate(r.plannedEndDate());
        audit.setActualDate(r.actualDate());
        audit.setLeadAuditor(r.leadAuditor());
        audit.setAuditTeam(r.auditTeam());
        audit.setSiteAddress(r.siteAddress() != null ? r.siteAddress() : farm.getAddress());
        audit.setProductsInScope(r.productsInScope() != null ? r.productsInScope() : farm.getCrops());
    }

    private String nextReference(Long orgId) {
        return String.format("AUD-%d-%04d", LocalDate.now().getYear(), referenceSequences.nextAuditNumber(orgId));
    }

    private AuditSummaryDto toSummary(Audit a) {
        int total = controlPoints.findByStandardCode(a.getStandard().getCode()).size();
        int assessed = (int) results.findByAuditId(a.getId()).stream()
                .filter(r -> r.getCompliance() != ComplianceResult.NOT_ASSESSED).count();
        int progress = total == 0 ? 0 : Math.round(100f * assessed / total);
        return new AuditSummaryDto(
                a.getId(), a.getReference(), a.getFarm().getId(), a.getFarm().getLegalName(),
                a.getStandard().getCode(), a.getStandard().getName(), a.getAuditType().name(), a.getStatus().name(),
                a.getPlannedStartDate(), a.getActualDate(), a.getLeadAuditor(), a.getCreatedAt(),
                findings.countByAuditId(a.getId()), assessed, total, progress);
    }

    private AuditDetailDto toDetail(Audit a) {
        int total = controlPoints.findByStandardCode(a.getStandard().getCode()).size();
        int assessed = (int) results.findByAuditId(a.getId()).stream()
                .filter(r -> r.getCompliance() != ComplianceResult.NOT_ASSESSED).count();
        int progress = total == 0 ? 0 : Math.round(100f * assessed / total);
        Farm f = a.getFarm();
        FarmDto farmDto = new FarmDto(f.getId(), f.getLegalName(), f.getTradeName(), f.getRegistrationNumber(),
                f.getAddress(), f.getCity(), f.getRegion(), f.getCountry(), f.getLatitude(), f.getLongitude(),
                f.getTotalHectares(), f.getCrops(), f.getProductionAreas(), f.getContactName(),
                f.getContactEmail(), f.getContactPhone(), f.getNotes());
        return new AuditDetailDto(
                a.getId(), a.getReference(), a.getStandard().getCode(), a.getStandard().getName(),
                a.getStandard().getVersion(), a.getAuditType().name(), a.getStatus().name(),
                a.getScopeDescription(), a.getPlannedStartDate(), a.getPlannedEndDate(), a.getActualDate(),
                a.getLeadAuditor(), a.getAuditTeam(), a.getSiteAddress(), a.getProductsInScope(),
                a.getCreatedAt(), a.getUpdatedAt(), farmDto, total, assessed, progress,
                findings.countByAuditId(a.getId()));
    }
}
