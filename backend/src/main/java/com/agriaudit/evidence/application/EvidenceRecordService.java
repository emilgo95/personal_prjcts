package com.agriaudit.evidence.application;

import com.agriaudit.audit.AuditDtos.AuditSummaryDto;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.evidence.EvidenceDtos.RecordChangeDto;
import com.agriaudit.evidence.EvidenceDtos.RecordDetailDto;
import com.agriaudit.evidence.EvidenceDtos.RecordDto;
import com.agriaudit.evidence.EvidenceDtos.RecordRequest;
import com.agriaudit.evidence.EvidenceDtos.TypeDto;
import com.agriaudit.evidence.EvidenceDtos.UseDto;
import com.agriaudit.evidence.domain.EvidenceRules;
import com.agriaudit.evidence.infrastructure.persistence.AuditEvidenceLink;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecord;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceType;
import com.agriaudit.evidence.infrastructure.persistence.AuditLinkRepository;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecordRepository;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceTypeRepository;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.storage.AttachmentStore.NewAttachment;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Alta, cambio, consulta y borrado de los registros de evidencia de una organización. */
@Service
@Transactional
public class EvidenceRecordService {

    private final EvidenceRecordRepository records;
    private final AuditLinkRepository links;
    private final EvidenceTypeRepository types;
    private final FarmDirectory farms;
    private final AttachmentStore attachments;
    private final AuditLookup audits;
    private final CatalogLookup catalog;
    private final ChangeRecorder trace;
    private final int windowDays;

    public EvidenceRecordService(EvidenceRecordRepository records, AuditLinkRepository links, EvidenceTypeRepository types,
                                 FarmDirectory farms, AttachmentStore attachments, AuditLookup audits,
                                 CatalogLookup catalog, ChangeRecorder trace,
                                 @Value("${evidence.renewal-window-days:30}") int windowDays) {
        this.records = records;
        this.links = links;
        this.types = types;
        this.farms = farms;
        this.attachments = attachments;
        this.audits = audits;
        this.catalog = catalog;
        this.trace = trace;
        this.windowDays = windowDays;
    }

    @Transactional(readOnly = true)
    public List<TypeDto> types() {
        return types.findAllByOrderBySortOrderAsc().stream().map(EvidenceRecordService::toTypeDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordDto> list(Long farmId, String typeCode) {
        Long orgId = SecurityUtils.currentOrganizationId();
        List<EvidenceRecord> found = farmId == null
                ? records.findByOrganizationIdOrderByIssuedOnDescIdDesc(orgId)
                : records.findByOrganizationIdAndFarmIdOrderByIssuedOnDescIdDesc(orgId, farmId);
        if (typeCode != null && !typeCode.isBlank()) {
            found = found.stream().filter(r -> r.getTypeCode().equals(typeCode)).toList();
        }
        return toDtos(orgId, found);
    }

    @Transactional(readOnly = true)
    public RecordDetailDto get(Long id) {
        EvidenceRecord record = require(id);
        Long orgId = record.getOrganizationId();
        return new RecordDetailDto(toDtos(orgId, List.of(record)).get(0),
                attachments.byEntity(AttachmentEntityType.EVIDENCE_RECORD, id), uses(record));
    }

    public RecordChangeDto create(RecordRequest request) {
        Long orgId = SecurityUtils.currentOrganizationId();
        EvidenceType type = requireType(request.typeCode());
        FarmDto farm = requireFarm(orgId, request.farmId());
        EvidenceRecord record = new EvidenceRecord(orgId, farm.id(), type.getCode(), SecurityUtils.currentUserId());
        apply(record, request);
        EvidenceRecord saved = records.save(record);
        trace.record(ChangeEntry.of(TraceEntity.EVIDENCE_RECORD, saved.getId(), shortRef(saved.getTitle()), null,
                TraceAction.CREATE, "Registró la evidencia «" + saved.getTitle() + "» (" + type.getName()
                        + ") de la explotación «" + farm.legalName() + "»",
                Changes.create().initial("title", "Título", saved.getTitle())
                        .initial("typeCode", "Tipo", type.getName())
                        .initial("issuedOn", "Fecha del documento", saved.getIssuedOn())
                        .initial("validUntil", "Válido hasta", saved.getValidUntil())
                        .initial("outcome", "Resultado", outcomeLabel(saved.getOutcome()))
                        .list()));
        return new RecordChangeDto(toDtos(orgId, List.of(saved)).get(0), 0);
    }

    public RecordChangeDto update(Long id, RecordRequest request) {
        EvidenceRecord record = require(id);
        Long orgId = record.getOrganizationId();
        List<AuditEvidenceLink> used = links.findByRecordId(id);
        if (!used.isEmpty() && (!record.getFarmId().equals(request.farmId()) || !record.getTypeCode().equals(request.typeCode()))) {
            throw new ConflictException("No se puede cambiar la explotación ni el tipo de un registro que ya se usa en "
                    + used.size() + " punto(s) de auditorías. Quita antes esos vínculos.");
        }
        requireType(request.typeCode());
        requireFarm(orgId, request.farmId());
        String beforeOutcome = record.getOutcome();
        String before = record.getTitle();
        LocalDate beforeIssued = record.getIssuedOn();
        LocalDate beforeValid = record.getValidUntil();
        boolean beforePersonal = record.isContainsPersonalData();
        String beforeNotes = record.getNotes();
        apply(record, request);
        EvidenceRecord saved = records.save(record);
        Changes c = Changes.create()
                .track("title", "Título", before, saved.getTitle())
                .track("issuedOn", "Fecha del documento", beforeIssued, saved.getIssuedOn())
                .track("validUntil", "Válido hasta", beforeValid, saved.getValidUntil())
                .track("outcome", "Resultado", outcomeLabel(beforeOutcome), outcomeLabel(saved.getOutcome()))
                .track("containsPersonalData", "Contiene datos personales", beforePersonal ? "Sí" : "No",
                        saved.isContainsPersonalData() ? "Sí" : "No")
                .personal("notes", "Notas", beforeNotes, saved.getNotes());
        int affected = (int) used.stream().map(AuditEvidenceLink::getAuditId).distinct().count();
        if (!c.isEmpty()) {
            trace.record(ChangeEntry.of(TraceEntity.EVIDENCE_RECORD, saved.getId(), shortRef(saved.getTitle()), null,
                    TraceAction.UPDATE, "Modificó la evidencia «" + saved.getTitle() + "»"
                            + (affected > 0 ? " (afecta a " + affected + " auditoría(s))" : ""), c.list()));
        }
        return new RecordChangeDto(toDtos(orgId, List.of(saved)).get(0), affected);
    }

    public void delete(Long id) {
        EvidenceRecord record = require(id);
        int used = links.findByRecordId(id).size();
        if (used > 0) {
            throw new ConflictException("No se puede borrar: el registro se usa en " + used
                    + " punto(s) de auditorías. Quita antes esos vínculos.");
        }
        attachments.deleteByEntity(AttachmentEntityType.EVIDENCE_RECORD, id);
        records.delete(record);
        trace.record(ChangeEntry.of(TraceEntity.EVIDENCE_RECORD, id, shortRef(record.getTitle()), null,
                TraceAction.DELETE, "Eliminó la evidencia «" + record.getTitle() + "»",
                Changes.create().track("title", "Título", record.getTitle(), null).list()));
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> documents(Long id) {
        require(id);
        return attachments.byEntity(AttachmentEntityType.EVIDENCE_RECORD, id);
    }

    public AttachmentDto uploadDocument(Long id, MultipartFile file) {
        EvidenceRecord record = require(id);
        if (file == null) {
            throw new BadRequestException("El fichero está vacío");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fichero subido", e);
        }
        AttachmentDto stored = attachments.store(new NewAttachment(record.getOrganizationId(),
                SecurityUtils.currentUsername(), AttachmentEntityType.EVIDENCE_RECORD, id, file.getOriginalFilename(),
                file.getContentType(), bytes, record.isContainsPersonalData()));
        trace.record(new ChangeEntry(TraceEntity.ATTACHMENT, stored.id(), stored.fileName(), TraceEntity.EVIDENCE_RECORD,
                id, null, TraceAction.UPLOAD, "Subió el documento «" + stored.fileName() + "» a la evidencia «"
                + record.getTitle() + "»", Changes.create().initial("file", "Fichero", stored.fileName()).list()));
        return stored;
    }

    // ---- utilidades compartidas con los otros servicios del módulo

    EvidenceRecord require(Long id) {
        return records.findByIdAndOrganizationId(id, SecurityUtils.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Evidencia no encontrada: " + id));
    }

    private EvidenceType requireType(String code) {
        return types.findById(code).orElseThrow(() -> new BadRequestException("Tipo de evidencia no válido: " + code));
    }

    private FarmDto requireFarm(Long orgId, Long farmId) {
        return farms.findFarm(orgId, farmId).orElseThrow(() -> new BadRequestException("Explotación no encontrada: " + farmId));
    }

    private static void apply(EvidenceRecord record, RecordRequest r) {
        if (r.validUntil() != null && r.validUntil().isBefore(r.issuedOn())) {
            throw new BadRequestException("La fecha de fin de vigencia no puede ser anterior a la del documento");
        }
        String outcome = r.outcome() == null || r.outcome().isBlank() ? EvidenceRecord.UNRATED : r.outcome().trim().toUpperCase();
        if (!Set.of(EvidenceRecord.FAVORABLE, EvidenceRecord.UNFAVORABLE, EvidenceRecord.UNRATED).contains(outcome)) {
            throw new BadRequestException("Resultado no válido: " + r.outcome());
        }
        record.setTitle(r.title().trim());
        record.setIssuedOn(r.issuedOn());
        record.setValidUntil(r.validUntil());
        record.setOutcome(outcome);
        record.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        record.setContainsPersonalData(Boolean.TRUE.equals(r.containsPersonalData()));
    }

    private List<RecordDto> toDtos(Long orgId, Collection<EvidenceRecord> list) {
        Map<Long, FarmDto> farmNames = farms.findFarms(orgId, list.stream().map(EvidenceRecord::getFarmId).collect(Collectors.toSet()));
        Map<String, EvidenceType> typeMap = types.findAll().stream().collect(Collectors.toMap(EvidenceType::getCode, Function.identity()));
        Map<Long, Long> usage = links.findByRecordIdIn(list.stream().map(EvidenceRecord::getId).toList()).stream()
                .collect(Collectors.groupingBy(AuditEvidenceLink::getRecordId, Collectors.counting()));
        LocalDate today = LocalDate.now();
        return list.stream().map(r -> new RecordDto(r.getId(), r.getFarmId(),
                farmNames.containsKey(r.getFarmId()) ? farmNames.get(r.getFarmId()).legalName() : null,
                r.getTypeCode(), typeMap.get(r.getTypeCode()) == null ? r.getTypeCode() : typeMap.get(r.getTypeCode()).getName(),
                r.getTitle(), r.getIssuedOn(), r.getValidUntil(), r.getOutcome(), r.getNotes(), r.isContainsPersonalData(),
                EvidenceRules.statusOf(r.getValidUntil(), today, windowDays),
                attachments.byEntity(AttachmentEntityType.EVIDENCE_RECORD, r.getId()).size(),
                usage.getOrDefault(r.getId(), 0L).intValue(), r.getCreatedAt(), r.getUpdatedAt())).toList();
    }

    private List<UseDto> uses(EvidenceRecord record) {
        List<AuditEvidenceLink> used = links.findByRecordId(record.getId());
        if (used.isEmpty()) {
            return List.of();
        }
        Map<Long, AuditSummaryDto> auditMap = audits.summaries(used.stream().map(AuditEvidenceLink::getAuditId).collect(Collectors.toSet()));
        Map<Long, ControlPointView> cps = catalog.findControlPoints(new HashSet<>(used.stream().map(AuditEvidenceLink::getControlPointId).toList()));
        return used.stream().map(l -> {
            AuditSummaryDto a = auditMap.get(l.getAuditId());
            ControlPointView cp = cps.get(l.getControlPointId());
            return new UseDto(l.getId(), l.getAuditId(), a == null ? null : a.reference(), a == null ? null : a.status(),
                    l.getControlPointId(), cp == null ? null : cp.ref(), cp == null ? null : cp.standardCode());
        }).filter(u -> u.auditReference() != null).toList();
    }

    static TypeDto toTypeDto(EvidenceType t) {
        return new TypeDto(t.getCode(), t.getName(), t.getDescription(), t.getPersonalDataNotice());
    }

    static String shortRef(String title) {
        return title.length() <= 100 ? title : title.substring(0, 100);
    }

    static String outcomeLabel(String outcome) {
        return switch (Objects.requireNonNullElse(outcome, EvidenceRecord.UNRATED)) {
            case EvidenceRecord.FAVORABLE -> "Favorable";
            case EvidenceRecord.UNFAVORABLE -> "Desfavorable";
            default -> "Sin valorar";
        };
    }
}
