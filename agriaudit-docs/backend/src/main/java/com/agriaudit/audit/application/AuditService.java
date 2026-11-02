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
    private final AuditTrace trace;

    public AuditService(AuditRepository audits, FarmDirectory farms,
                        CatalogLookup catalog, AuditItemResultRepository results,
                        FindingsCounter findings, ScoringService scoringService,
                        AttachmentStore attachmentLookup, AuditReferenceSequence referenceSequences,
                        AuditTrace trace) {
        this.audits = audits;
        this.farms = farms;
        this.catalog = catalog;
        this.results = results;
        this.findings = findings;
        this.scoringService = scoringService;
        this.attachmentLookup = attachmentLookup;
        this.referenceSequences = referenceSequences;
        this.trace = trace;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditSummaryDto> list() {
        return summarize(audits.findByOrganizationIdOrderByCreatedAtDesc(SecurityUtils.currentOrganizationId()));
    }

    /** Una página de auditorías, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public AuditPage page(int page, int size) {
        return page(page, size, null);
    }

    /**
     * Página de auditorías de la organización, de la más reciente a la más antigua. Con {@code query}, solo las que
     * contienen todas sus palabras en la referencia, la explotación (razón social, nombre comercial, n.º de registro,
     * localidad o región), el estándar, el auditor líder, el tipo o el estado (ver {@link AuditSearch}).
     */
    @Transactional(readOnly = true)
    public AuditPage page(int page, int size, String query) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Long orgId = SecurityUtils.currentOrganizationId();
        var pageRequest = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "createdAt", "id"));
        List<String> terms = AuditSearch.terms(query);
        org.springframework.data.domain.Page<Audit> result;
        if (terms.isEmpty()) {
            result = audits.findByOrganizationId(orgId, pageRequest);
        } else {
            List<Long> matching = matchingAuditIds(orgId, terms);
            result = matching.isEmpty()
                    ? org.springframework.data.domain.Page.empty(pageRequest)
                    : audits.findByOrganizationIdAndIdIn(orgId, matching, pageRequest);
        }
        return new AuditPage(summarize(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** Ids de las auditorías de la organización que contienen todas las palabras buscadas. */
    private List<Long> matchingAuditIds(Long orgId, List<String> terms) {
        List<Audit> all = audits.findByOrganizationIdOrderByCreatedAtDesc(orgId);
        Map<Long, FarmDto> farmsById = farms.findFarms(orgId, all.stream().map(Audit::getFarmId).distinct().toList());
        Map<Long, StandardView> standards = new HashMap<>();
        List<Long> out = new ArrayList<>();
        for (Audit a : all) {
            StandardView standard = standards.computeIfAbsent(a.getStandardId(), id -> standardOf(a));
            FarmDto farm = farmsById.get(a.getFarmId());
            String text = AuditSearch.text(a.getReference(), a.getLeadAuditor(), standard.code(), standard.name(),
                    typeLabel(a.getAuditType()), stateLabel(a.getStatus()),
                    farm == null ? null : farm.legalName(), farm == null ? null : farm.tradeName(),
                    farm == null ? null : farm.registrationNumber(), farm == null ? null : farm.city(),
                    farm == null ? null : farm.region());
            if (AuditSearch.matches(text, terms)) {
                out.add(a.getId());
            }
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : audits.countByStatus(SecurityUtils.currentOrganizationId())) {
            counts.put(((AuditStatus) row[0]).name(), (Long) row[1]);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditSummaryDto> recent(int limit) {
        return page(0, limit).content();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, AuditSummaryDto> summaries(java.util.Collection<Long> auditIds) {
        if (auditIds.isEmpty()) {
            return Map.of();
        }
        List<Audit> found = audits.findByIdInAndOrganizationId(auditIds, SecurityUtils.currentOrganizationId());
        Map<Long, AuditSummaryDto> byId = new HashMap<>();
        summarize(found).forEach(s -> byId.put(s.id(), s));
        return byId;
    }

    /** Resúmenes de varias auditorías con un número fijo de consultas (sin una por auditoría). */
    private List<AuditSummaryDto> summarize(List<Audit> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Long orgId = SecurityUtils.currentOrganizationId();
        List<Long> ids = list.stream().map(Audit::getId).toList();
        Map<Long, FarmDto> farmsById = farms.findFarms(orgId, list.stream().map(Audit::getFarmId).distinct().toList());
        Map<Long, Long> assessedByAudit = new HashMap<>();
        for (Object[] row : results.countAssessedByAudit(ids)) {
            assessedByAudit.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, Long> findingsByAudit = findings.countByAudits(ids);
        Map<Long, StandardView> standards = new HashMap<>();
        Map<String, Integer> totals = new HashMap<>();
        List<AuditSummaryDto> out = new ArrayList<>(list.size());
        for (Audit a : list) {
            StandardView standard = standards.computeIfAbsent(a.getStandardId(), id -> standardOf(a));
            int total = totals.computeIfAbsent(standard.code(), c -> catalog.controlPointsOf(c).size());
            int assessed = assessedByAudit.getOrDefault(a.getId(), 0L).intValue();
            FarmDto farm = farmsById.get(a.getFarmId());
            out.add(new AuditSummaryDto(
                    a.getId(), a.getReference(), a.getFarmId(), farm == null ? null : farm.legalName(),
                    standard.code(), standard.name(), a.getAuditType().name(), a.getStatus().name(),
                    a.getPlannedStartDate(), a.getActualDate(), a.getLeadAuditor(), a.getCreatedAt(),
                    findingsByAudit.getOrDefault(a.getId(), 0L), assessed, total,
                    total == 0 ? 0 : Math.round(100f * assessed / total)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public AuditDetailDto get(Long id) {
        return toDetail(requireAuditEntity(id));
    }

    public AuditDetailDto create(AuditRequest request) {
        Long orgId = SecurityUtils.currentOrganizationId();
        FarmDto farm = requireFarm(orgId, request.farmId());
        StandardView standard = catalog.findAvailableStandard(request.standardCode())
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
        Audit saved = audits.save(audit);
        trace.created(saved, farm.legalName(), standard.name());
        return toDetail(saved);
    }

    public AuditDetailDto update(Long id, AuditRequest request) {
        Audit audit = requireAuditEntity(id);
        requireEditable(audit);
        if (!standardOf(audit).code().equals(request.standardCode())) {
            throw new BadRequestException("No se puede cambiar el estándar de una auditoría existente");
        }
        FarmDto farm = requireFarm(audit.getOrganizationId(), request.farmId());
        AuditTrace.AuditSnap before = AuditTrace.snapshot(audit);
        audit.setFarmId(farm.id());
        audit.setAuditType(parseType(request.auditType()));
        applyRequest(audit, request, farm);
        Audit saved = audits.save(audit);
        trace.updated(before, saved);
        return toDetail(saved);
    }

    /**
     * Cambia el estado siguiendo el flujo previsto (borrador, en curso, cierre, enviada, cerrada), con la
     * posibilidad de reabrir un paso atrás. Pasar a cierre exige haber evaluado todos los puntos de control
     * y cerrar la auditoría exige que no queden no conformidades sin cerrar ni verificar.
     */
    public AuditDetailDto transition(Long id, String status) {
        return transition(id, status, null);
    }

    /** Lo que impide (NC por revisar) o condiciona (NC vencidas sin justificar) el envío de la auditoría. */
    @Transactional(readOnly = true)
    public FindingsCounter.SubmissionCheck submissionCheck(Long id) {
        return findings.submissionCheck(requireAuditEntity(id).getId());
    }

    /**
     * Como {@link #transition(Long, String)}; al enviar, {@code lateJustifications} justifica las NC vencidas
     * (id de la NC → motivo).
     */
    public AuditDetailDto transition(Long id, String status, java.util.Map<Long, String> lateJustifications) {
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
        if (SecurityUtils.currentPrincipal().getRole() == com.agriaudit.identity.Role.QUALITY
                && target != AuditStatus.SUBMITTED && target != AuditStatus.CLOSED
                && !(current == AuditStatus.SUBMITTED && target == AuditStatus.CLOSING)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El responsable de calidad solo puede enviar, cerrar o reabrir (desde Enviada) una auditoría");
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
        if (target == AuditStatus.SUBMITTED) {
            requireFindingForEveryNonCompliance(audit);
            requireReviewedAndJustifiedFindings(audit, lateJustifications);
        }
        if (target == AuditStatus.CLOSED) {
            long open = findings.countOpenByAudit(audit.getId());
            if (open > 0) {
                throw new ConflictException("No se puede cerrar la auditoría: quedan " + open
                        + (open == 1 ? " no conformidad" : " no conformidades")
                        + " sin cerrar ni verificar. Reábrela (pasar a «Cierre») para resolverlas.");
            }
        }
        audit.setStatus(target);
        Audit saved = audits.save(audit);
        trace.transitioned(saved, current, target);
        return toDetail(saved);
    }

    /**
     * Para enviar, ninguna NC puede estar pendiente de verificar (acción completada o marcada como resuelta), y
     * cada NC vencida y sin cerrar necesita una justificación del retraso, que se guarda en la NC.
     */
    private void requireReviewedAndJustifiedFindings(Audit audit, java.util.Map<Long, String> lateJustifications) {
        FindingsCounter.SubmissionCheck check = findings.submissionCheck(audit.getId());
        if (!check.toReview().isEmpty()) {
            int n = check.toReview().size();
            throw new ConflictException("No se puede enviar la auditoría: " + (n == 1
                    ? "hay 1 no conformidad por revisar (" : "hay " + n + " no conformidades por revisar (")
                    + references(check.toReview()) + "): su acción correctiva está completada pero falta "
                    + "verificarla en «Hallazgos y CAPA».");
        }
        if (lateJustifications != null && !lateJustifications.isEmpty()) {
            findings.justifyLate(audit.getId(), lateJustifications);
            check = findings.submissionCheck(audit.getId());
        }
        List<FindingsCounter.PendingFinding> unjustified = check.overdue().stream()
                .filter(f -> f.lateJustification() == null || f.lateJustification().isBlank()).toList();
        if (!unjustified.isEmpty()) {
            int n = unjustified.size();
            throw new ConflictException("No se puede enviar la auditoría: " + (n == 1
                    ? "hay 1 no conformidad vencida sin justificar (" : "hay " + n
                    + " no conformidades vencidas sin justificar (") + references(unjustified)
                    + "). Indica el motivo del retraso de cada una.");
        }
    }

    private static String references(List<FindingsCounter.PendingFinding> list) {
        return list.stream().map(FindingsCounter.PendingFinding::reference)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** Un punto evaluado como no conforme debe tener su no conformidad registrada antes de enviar la auditoría. */
    private void requireFindingForEveryNonCompliance(Audit audit) {
        java.util.Set<Long> withFinding = findings.controlPointIdsWithFinding(audit.getId());
        long missing = results.findByAuditId(audit.getId()).stream()
                .filter(r -> r.getCompliance() == ComplianceResult.NON_COMPLIANT)
                .filter(r -> !withFinding.contains(r.getControlPointId()))
                .count();
        if (missing > 0) {
            throw new ConflictException("No se puede enviar la auditoría: hay " + missing
                    + (missing == 1 ? " punto no conforme" : " puntos no conformes")
                    + " sin no conformidad registrada. Regístrala en «Hallazgos y CAPA».");
        }
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

    static String stateLabel(AuditStatus s) {
        return switch (s) {
            case DRAFT -> "Borrador";
            case IN_PROGRESS -> "En curso";
            case CLOSING -> "Cierre";
            case SUBMITTED -> "Enviada";
            case CLOSED -> "Cerrada";
        };
    }

    static String typeLabel(AuditType t) {
        return switch (t) {
            case SELF_ASSESSMENT -> "Autoevaluación";
            case INTERNAL -> "Auditoría interna";
            case CB_INITIAL -> "Certificación inicial";
            case SURVEILLANCE -> "Seguimiento";
            case RECERTIFICATION -> "Recertificación";
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
        requireEditable(audit);
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
            AuditTrace.ResultSnap before = r.getId() == null ? null
                    : new AuditTrace.ResultSnap(r.getCompliance(), r.getEvidenceNotes(), r.getAuditorComment());
            r.setCompliance(compliance);
            r.setEvidenceNotes(u.evidenceNotes());
            r.setAuditorComment(u.auditorComment());
            r.setAssessedAt(Instant.now());
            r.setAssessedBy(user);
            results.save(r);
            String pointRef = catalog.findControlPoint(u.controlPointId()).map(ControlPointView::ref)
                    .orElse(String.valueOf(u.controlPointId()));
            trace.resultChanged(audit, pointRef, before,
                    new AuditTrace.ResultSnap(compliance, u.evidenceNotes(), u.auditorComment()));
        }
        if (audit.getStatus() == AuditStatus.DRAFT) {
            audit.setStatus(AuditStatus.IN_PROGRESS);
            audits.save(audit);
            trace.transitioned(audit, AuditStatus.DRAFT, AuditStatus.IN_PROGRESS);
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

    /** Entidad de la auditoría para modificarla: 409 si está enviada o cerrada. */
    @Transactional(readOnly = true)
    Audit requireEditableAuditEntity(Long id) {
        Audit audit = requireAuditEntity(id);
        requireEditable(audit);
        return audit;
    }

    private static void requireEditable(Audit audit) {
        if (audit.getStatus().isLocked()) {
            throw new ConflictException(audit.getStatus().lockedMessage());
        }
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
        if (r.plannedStartDate() != null && r.plannedEndDate() != null
                && r.plannedEndDate().isBefore(r.plannedStartDate())) {
            throw new BadRequestException("La fecha prevista de fin no puede ser anterior a la de inicio");
        }
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
                a.getStatus(), a.getPlannedStartDate(), a.getLeadAuditor(), a.getActualDate());
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
