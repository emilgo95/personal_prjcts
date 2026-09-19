package com.agriaudit.audit.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.audit.AuditStatus;
import com.agriaudit.audit.FindingsCounter;
import com.agriaudit.audit.domain.AuditType;
import com.agriaudit.audit.domain.ComplianceResult;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.audit.AuditDtos.*;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.audit.ScoreDtos.ScoreResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.catalog.StandardView;
import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.domain.AuditItemResult;
import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.audit.infrastructure.persistence.AuditRepository;
import com.agriaudit.audit.infrastructure.persistence.AuditReferenceSequence;

@Service
@Transactional
public class AuditService implements AuditLookup {

    private final AuditRepository audits;
    private final FarmDirectory farms;
    private final CatalogLookup catalog;
    private final AuditItemResultRepository results;
    private final FindingsCounter findings;
    private final ScoringService scoringService;
    private final AttachmentStore attachmentLookup;
    private final AuditReferenceSequence referenceSequences;

    public AuditService(AuditRepository audits, FarmDirectory farms,
                        CatalogLookup catalog, AuditItemResultRepository results,
                        FindingsCounter findings, ScoringService scoringService,
                        AttachmentStore attachmentLookup, AuditReferenceSequence referenceSequences) {
        this.audits = audits;
        this.farms = farms;
        this.catalog = catalog;
        this.results = results;
        this.findings = findings;
        this.scoringService = scoringService;
        this.attachmentLookup = attachmentLookup;
        this.referenceSequences = referenceSequences;
    }

    @Transactional(readOnly = true)
    public List<AuditSummaryDto> list() {
        Long orgId = SecurityUtils.currentOrganizationId();
        List<Audit> list = audits.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        Map<Long, FarmDto> farmsById = farms.findFarms(orgId, list.stream().map(Audit::getFarmId).distinct().toList());
        return list.stream().map(a -> toSummary(a, farmsById.get(a.getFarmId()))).toList();
    }

    @Transactional(readOnly = true)
    public AuditDetailDto get(Long id) {
        return toDetail(requireAuditEntity(id));
    }

    public AuditDetailDto create(AuditRequest request) {
        Long orgId = SecurityUtils.currentOrganizationId();
        FarmDto farm = requireFarm(orgId, request.farmId());
        StandardView standard = catalog.findStandard(request.standardCode())
                .orElseThrow(() -> new NotFoundException("Estándar no encontrado: " + request.standardCode()));

        Audit audit = new Audit();
        audit.setOrganizationId(orgId);
        audit.setFarmId(farm.id());
        audit.setStandardId(standard.id());
        audit.setReference(nextReference(orgId));
        audit.setAuditType(parseType(request.auditType()));
        audit.setStatus(AuditStatus.DRAFT);
        audit.setCreatedByUserId(SecurityUtils.currentUserId());
        applyRequest(audit, request, farm);
        return toDetail(audits.save(audit));
    }

    public AuditDetailDto update(Long id, AuditRequest request) {
        Audit audit = requireAuditEntity(id);
        if (!standardOf(audit).code().equals(request.standardCode())) {
            throw new BadRequestException("No se puede cambiar el estándar de una auditoría existente");
        }
        FarmDto farm = requireFarm(audit.getOrganizationId(), request.farmId());
        audit.setFarmId(farm.id());
        audit.setAuditType(parseType(request.auditType()));
        applyRequest(audit, request, farm);
        return toDetail(audits.save(audit));
    }

    /**
     * Cambia el estado siguiendo el flujo previsto (borrador, en curso, cierre, enviada, cerrada), con la
     * posibilidad de reabrir un paso atrás. Pasar a cierre exige haber evaluado todos los puntos de control
     * y cerrar la auditoría exige que no queden no conformidades sin cerrar ni verificar.
     */
    public AuditDetailDto transition(Long id, String status) {
        Audit audit = requireAuditEntity(id);
        AuditStatus target;
        try {
            target = AuditStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Estado no válido: " + status);
        }
        AuditStatus current = audit.getStatus();
        if (current == target) {
            throw new BadRequestException("La auditoría ya está en estado «" + stateLabel(target) + "».");
        }
        if (!allowedTransitions(current).contains(target)) {
            throw new ConflictException("No se puede pasar de «" + stateLabel(current) + "» a «"
                    + stateLabel(target) + "». Estados permitidos: " + allowedLabels(current) + ".");
        }
        if (target == AuditStatus.CLOSING) {
            int total = catalog.controlPointsOf(standardOf(audit).code()).size();
            long assessed = results.findByAuditId(audit.getId()).stream()
                    .filter(r -> r.getCompliance() != ComplianceResult.NOT_ASSESSED).count();
            if (assessed < total) {
                throw new ConflictException("No se puede pasar a cierre: faltan " + (total - assessed)
                        + " puntos de control por evaluar.");
            }
        }
        if (target == AuditStatus.CLOSED) {
            long open = findings.countOpenByAudit(audit.getId());
            if (open > 0) {
                throw new ConflictException("No se puede cerrar la auditoría: quedan " + open
                        + (open == 1 ? " no conformidad" : " no conformidades") + " sin cerrar ni verificar.");
            }
        }
        audit.setStatus(target);
        return toDetail(audits.save(audit));
    }

    /** Estados a los que puede pasar una auditoría desde {@code from}. */
    static List<AuditStatus> allowedTransitions(AuditStatus from) {
        return switch (from) {
            case DRAFT -> List.of(AuditStatus.IN_PROGRESS);
            case IN_PROGRESS -> List.of(AuditStatus.CLOSING);
            case CLOSING -> List.of(AuditStatus.SUBMITTED, AuditStatus.IN_PROGRESS);
            case SUBMITTED -> List.of(AuditStatus.CLOSED, AuditStatus.CLOSING);
            case CLOSED -> List.of();
        };
    }

    private static String stateLabel(AuditStatus s) {
        return switch (s) {
            case DRAFT -> "Borrador";
            case IN_PROGRESS -> "En curso";
            case CLOSING -> "Cierre";
            case SUBMITTED -> "Enviada";
            case CLOSED -> "Cerrada";
        };
    }

    private static String allowedLabels(AuditStatus from) {
        List<AuditStatus> allowed = allowedTransitions(from);
        if (allowed.isEmpty()) {
            return "ninguno, la auditoría está cerrada";
        }
        return allowed.stream().map(AuditService::stateLabel).collect(java.util.stream.Collectors.joining(", "));
    }

    // ---- resultados del checklist ----

    @Transactional(readOnly = true)
    public List<AuditItemResultDto> getResults(Long id) {
        Audit audit = requireAuditEntity(id);
        Map<Long, AuditItemResult> existing = new HashMap<>();
        for (AuditItemResult r : results.findByAuditId(audit.getId())) {
            existing.put(r.getControlPointId(), r);
        }
        List<AuditItemResultDto> out = new ArrayList<>();
        for (ControlPointView cp : catalog.controlPointsOf(standardOf(audit).code())) {
            AuditItemResult r = existing.get(cp.id());
            out.add(new AuditItemResultDto(
                    r == null ? null : r.getId(),
                    cp.id(), cp.ref(), cp.title(), cp.description(), cp.guidance(),
                    cp.level().name(), cp.applicableScopes(),
                    cp.chapterCode(), cp.chapterTitle(),
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
        Audit audit = requireAuditEntity(id);
        Map<Long, AuditItemResult> existing = new HashMap<>();
        for (AuditItemResult r : results.findByAuditId(audit.getId())) {
            existing.put(r.getControlPointId(), r);
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
                ControlPointView cp = catalog.findControlPoint(u.controlPointId())
                        .orElseThrow(() -> new NotFoundException("Punto de control no encontrado: " + u.controlPointId()));
                if (!cp.belongsTo(audit.getStandardId())) {
                    throw new BadRequestException("El punto de control no pertenece al estándar de la auditoría");
                }
                r = new AuditItemResult();
                r.setAuditId(audit.getId());
                r.setControlPointId(cp.id());
                existing.put(cp.id(), r);
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
        return scoringService.score(requireAuditEntity(id));
    }

    // ---- helpers ----

    @Override
    @Transactional(readOnly = true)
    public AuditRef requireAudit(Long auditId) {
        return toRef(requireAuditEntity(auditId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditRef> findByOrganization(Long organizationId) {
        return audits.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream().map(this::toRef).toList();
    }

    /** Entidad de la auditoría de la organización del usuario; solo para uso interno del módulo. */
    @Transactional(readOnly = true)
    Audit requireAuditEntity(Long id) {
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

    private FarmDto requireFarm(Long orgId, Long farmId) {
        return farms.findFarm(orgId, farmId)
                .orElseThrow(() -> new NotFoundException("Explotación no encontrada: " + farmId));
    }

    private void applyRequest(Audit audit, AuditRequest r, FarmDto farm) {
        audit.setScopeDescription(r.scopeDescription());
        audit.setPlannedStartDate(r.plannedStartDate());
        audit.setPlannedEndDate(r.plannedEndDate());
        audit.setActualDate(r.actualDate());
        audit.setLeadAuditor(r.leadAuditor());
        audit.setAuditTeam(r.auditTeam());
        audit.setSiteAddress(r.siteAddress() != null ? r.siteAddress() : farm.address());
        audit.setProductsInScope(r.productsInScope() != null ? r.productsInScope() : farm.crops());
    }

    private String nextReference(Long orgId) {
        return String.format("AUD-%d-%04d", LocalDate.now().getYear(), referenceSequences.nextAuditNumber(orgId));
    }

    /** Estándar de la auditoría, leído del catálogo por su id. */
    StandardView standardOf(Audit audit) {
        return catalog.findStandard(audit.getStandardId())
                .orElseThrow(() -> new IllegalStateException("Estándar inexistente: " + audit.getStandardId()));
    }

    private AuditRef toRef(Audit a) {
        return new AuditRef(a.getId(), a.getOrganizationId(), a.getReference(), standardOf(a).code(),
                a.getStatus(), a.getPlannedStartDate(), a.getLeadAuditor());
    }

    private AuditSummaryDto toSummary(Audit a, FarmDto farm) {
        StandardView standard = standardOf(a);
        int total = catalog.controlPointsOf(standard.code()).size();
        int assessed = (int) results.findByAuditId(a.getId()).stream()
                .filter(r -> r.getCompliance() != ComplianceResult.NOT_ASSESSED).count();
        int progress = total == 0 ? 0 : Math.round(100f * assessed / total);
        return new AuditSummaryDto(
                a.getId(), a.getReference(), a.getFarmId(), farm == null ? null : farm.legalName(),
                standard.code(), standard.name(), a.getAuditType().name(), a.getStatus().name(),
                a.getPlannedStartDate(), a.getActualDate(), a.getLeadAuditor(), a.getCreatedAt(),
                findings.countByAudit(a.getId()), assessed, total, progress);
    }

    private AuditDetailDto toDetail(Audit a) {
        StandardView standard = standardOf(a);
        int total = catalog.controlPointsOf(standard.code()).size();
        int assessed = (int) results.findByAuditId(a.getId()).stream()
                .filter(r -> r.getCompliance() != ComplianceResult.NOT_ASSESSED).count();
        int progress = total == 0 ? 0 : Math.round(100f * assessed / total);
        FarmDto farmDto = requireFarm(a.getOrganizationId(), a.getFarmId());
        return new AuditDetailDto(
                a.getId(), a.getReference(), standard.code(), standard.name(),
                standard.version(), a.getAuditType().name(), a.getStatus().name(),
                a.getScopeDescription(), a.getPlannedStartDate(), a.getPlannedEndDate(), a.getActualDate(),
                a.getLeadAuditor(), a.getAuditTeam(), a.getSiteAddress(), a.getProductsInScope(),
                a.getCreatedAt(), a.getUpdatedAt(), farmDto, total, assessed, progress,
                findings.countByAudit(a.getId()),
                allowedTransitions(a.getStatus()).stream().map(AuditStatus::name).toList());
    }
}
