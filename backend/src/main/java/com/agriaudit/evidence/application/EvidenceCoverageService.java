package com.agriaudit.evidence.application;

import com.agriaudit.audit.AuditDtos.AuditDetailDto;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.evidence.EvidenceDtos.CoverageDto;
import com.agriaudit.evidence.EvidenceDtos.CoverageItemDto;
import com.agriaudit.evidence.EvidenceDtos.CoverageRecordDto;
import com.agriaudit.evidence.EvidenceDtos.LinkRequest;
import com.agriaudit.evidence.EvidenceDtos.TypeCoverageDto;
import com.agriaudit.evidence.domain.EvidenceRules;
import com.agriaudit.evidence.infrastructure.persistence.AuditEvidenceLink;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecord;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceType;
import com.agriaudit.evidence.infrastructure.persistence.MatrixEntry;
import com.agriaudit.evidence.infrastructure.persistence.AuditLinkRepository;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecordRepository;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceTypeRepository;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Qué registros de evidencia de la explotación pueden cubrir cada punto de control de una auditoría y cuáles ya se han
 * vinculado. La aplicación solo <b>sugiere</b>: una persona confirma cada vínculo y nada se marca conforme solo.
 */
@Service
@Transactional
public class EvidenceCoverageService {

    private final AuditLookup audits;
    private final CatalogLookup catalog;
    private final EvidenceMatrixService matrix;
    private final EvidenceRecordRepository records;
    private final EvidenceTypeRepository types;
    private final AuditLinkRepository links;
    private final AttachmentStore attachments;
    private final ChangeRecorder trace;
    private final int windowDays;

    public EvidenceCoverageService(AuditLookup audits, CatalogLookup catalog, EvidenceMatrixService matrix,
                                   EvidenceRecordRepository records, EvidenceTypeRepository types, AuditLinkRepository links,
                                   AttachmentStore attachments, ChangeRecorder trace,
                                   @Value("${evidence.renewal-window-days:30}") int windowDays) {
        this.audits = audits;
        this.catalog = catalog;
        this.matrix = matrix;
        this.records = records;
        this.types = types;
        this.links = links;
        this.attachments = attachments;
        this.trace = trace;
        this.windowDays = windowDays;
    }

    public CoverageDto coverage(Long auditId) {
        Long orgId = SecurityUtils.currentOrganizationId();
        AuditRef ref = audits.requireAudit(auditId);
        AuditDetailDto detail = audits.get(auditId);
        Long farmId = detail.farm().id();
        List<MatrixEntry> entries = matrix.entriesFor(orgId, ref.standardCode());
        Map<String, List<MatrixEntry>> byRef = entries.stream().collect(Collectors.groupingBy(MatrixEntry::getControlRef));
        Map<String, EvidenceType> typeMap = types.findAll().stream().collect(Collectors.toMap(EvidenceType::getCode, Function.identity()));
        List<EvidenceRecord> farmRecords = records.findByOrganizationIdAndFarmIdOrderByIssuedOnDescIdDesc(orgId, farmId);
        Map<String, List<EvidenceRecord>> recordsByType = farmRecords.stream().collect(Collectors.groupingBy(EvidenceRecord::getTypeCode));
        Map<Long, AuditEvidenceLink> linkByKey = new LinkedHashMap<>();
        List<AuditEvidenceLink> auditLinks = links.findByAuditId(auditId);
        Map<String, Integer> docs = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        List<CoverageItemDto> items = new ArrayList<>();
        for (ControlPointView cp : catalog.controlPointsOf(ref.standardCode())) {
            List<MatrixEntry> forCp = byRef.get(cp.ref());
            if (forCp == null) {
                continue;
            }
            List<TypeCoverageDto> typeCoverages = new ArrayList<>();
            boolean covered = false;
            boolean alert = false;
            for (MatrixEntry entry : forCp) {
                EvidenceType type = typeMap.get(entry.getTypeCode());
                List<EvidenceRecord> ofType = recordsByType.getOrDefault(entry.getTypeCode(), List.of());
                EvidenceRules.Health health = EvidenceRules.health(ofType.stream().map(EvidenceRecord::facts).toList(), today, windowDays);
                List<CoverageRecordDto> recs = new ArrayList<>();
                for (EvidenceRecord r : ofType) {
                    AuditEvidenceLink link = auditLinks.stream()
                            .filter(l -> l.getControlPointId().equals(cp.id()) && l.getRecordId().equals(r.getId())).findFirst().orElse(null);
                    String status = EvidenceRules.statusOf(r.getValidUntil(), today, windowDays);
                    String reason = notLinkableReason(r, status);
                    int documents = docs.computeIfAbsent(String.valueOf(r.getId()),
                            k -> attachments.byEntity(AttachmentEntityType.EVIDENCE_RECORD, r.getId()).size());
                    recs.add(new CoverageRecordDto(r.getId(), r.getTitle(), r.getIssuedOn(), r.getValidUntil(), status,
                            r.getOutcome(), documents, link == null ? null : link.getId(), reason == null, reason));
                    if (link != null) {
                        covered = true;
                        if (reason != null) {
                            alert = true;
                        }
                    }
                }
                typeCoverages.add(new TypeCoverageDto(entry.getTypeCode(), type == null ? entry.getTypeCode() : type.getName(),
                        entry.getStatus(), health.status(), recs));
            }
            items.add(new CoverageItemDto(cp.id(), cp.ref(), cp.title(), typeCoverages, covered, alert));
        }
        return new CoverageDto(auditId, farmId, ref.status().isLocked(), items);
    }

    /** Confirma que un registro cubre un punto de control de la auditoría. */
    public CoverageDto link(Long auditId, LinkRequest request) {
        Long orgId = SecurityUtils.currentOrganizationId();
        AuditRef audit = audits.requireAudit(auditId);
        requireEditable(audit);
        EvidenceRecord record = records.findByIdAndOrganizationId(request.recordId(), orgId)
                .orElseThrow(() -> new NotFoundException("Evidencia no encontrada: " + request.recordId()));
        AuditDetailDto detail = audits.get(auditId);
        if (!record.getFarmId().equals(detail.farm().id())) {
            throw new BadRequestException("La evidencia es de otra explotación distinta a la de la auditoría");
        }
        ControlPointView cp = catalog.findControlPoint(request.controlPointId())
                .filter(c -> c.standardCode().equals(audit.standardCode()))
                .orElseThrow(() -> new BadRequestException("El punto de control no pertenece a la norma de la auditoría"));
        boolean covers = matrix.entriesFor(orgId, audit.standardCode()).stream()
                .anyMatch(e -> e.getTypeCode().equals(record.getTypeCode()) && e.getControlRef().equals(cp.ref()));
        if (!covers) {
            throw new BadRequestException("Según la matriz de equivalencias, este tipo de evidencia no sirve para el punto " + cp.ref());
        }
        String reason = notLinkableReason(record, EvidenceRules.statusOf(record.getValidUntil(), LocalDate.now(), windowDays));
        if (reason != null) {
            throw new ConflictException(reason);
        }
        if (links.existsByAuditIdAndControlPointIdAndRecordId(auditId, cp.id(), record.getId())) {
            throw new ConflictException("La evidencia ya está vinculada a ese punto");
        }
        AuditEvidenceLink saved = links.save(new AuditEvidenceLink(orgId, auditId, cp.id(), record.getId(), SecurityUtils.currentUserId()));
        trace.record(new ChangeEntry(TraceEntity.EVIDENCE_LINK, saved.getId(), cp.ref(), TraceEntity.EVIDENCE_RECORD,
                record.getId(), auditId, TraceAction.CREATE, "Vinculó la evidencia «" + record.getTitle() + "» al punto "
                + cp.ref() + " de " + audit.reference(),
                Changes.create().initial("record", "Evidencia", record.getTitle()).initial("controlRef", "Punto de control", cp.ref()).list()));
        return coverage(auditId);
    }

    public CoverageDto unlink(Long auditId, Long linkId) {
        Long orgId = SecurityUtils.currentOrganizationId();
        AuditRef audit = audits.requireAudit(auditId);
        requireEditable(audit);
        AuditEvidenceLink link = links.findByIdAndOrganizationId(linkId, orgId)
                .filter(l -> l.getAuditId().equals(auditId))
                .orElseThrow(() -> new NotFoundException("Vínculo no encontrado: " + linkId));
        String title = records.findById(link.getRecordId()).map(EvidenceRecord::getTitle).orElse("evidencia " + link.getRecordId());
        String cpRef = catalog.findControlPoint(link.getControlPointId()).map(ControlPointView::ref).orElse("?");
        links.delete(link);
        trace.record(new ChangeEntry(TraceEntity.EVIDENCE_LINK, linkId, cpRef, TraceEntity.EVIDENCE_RECORD, link.getRecordId(),
                auditId, TraceAction.DELETE, "Quitó el vínculo de la evidencia «" + title + "» con el punto " + cpRef
                + " de " + audit.reference(), Changes.create().track("record", "Evidencia", title, null).list()));
        return coverage(auditId);
    }

    private static void requireEditable(AuditRef audit) {
        if (audit.status().isLocked()) {
            throw new ConflictException(audit.status().lockedMessage());
        }
    }

    /** Un registro desfavorable o vencido no puede cubrir un punto; devuelve el motivo o {@code null} si puede. */
    private static String notLinkableReason(EvidenceRecord r, String status) {
        if (EvidenceRecord.UNFAVORABLE.equals(r.getOutcome())) {
            return "El resultado del registro es desfavorable: no puede cubrir el punto.";
        }
        if (EvidenceRules.EXPIRED.equals(status)) {
            return "El registro está vencido: renuévalo para poder usarlo.";
        }
        return null;
    }
}
