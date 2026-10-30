package com.agriaudit.evidence.application;

import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.catalog.StandardView;
import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.evidence.EvidenceDtos.MatrixDto;
import com.agriaudit.evidence.EvidenceDtos.MatrixEntryDto;
import com.agriaudit.evidence.EvidenceDtos.MatrixEntryRequest;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecord;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceType;
import com.agriaudit.evidence.infrastructure.persistence.MatrixEntry;
import com.agriaudit.evidence.infrastructure.persistence.AuditLinkRepository;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecordRepository;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceTypeRepository;
import com.agriaudit.evidence.infrastructure.persistence.MatrixEntryRepository;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Matriz de equivalencias: qué tipo de evidencia sirve para qué punto de qué norma. Cada organización trabaja con su
 * propia copia, que se crea la primera vez que se usa a partir del borrador de partida.
 */
@Service
@Transactional
public class EvidenceMatrixService {

    private final MatrixEntryRepository matrix;
    private final EvidenceTypeRepository types;
    private final EvidenceRecordRepository records;
    private final AuditLinkRepository links;
    private final CatalogLookup catalog;
    private final ChangeRecorder trace;

    public EvidenceMatrixService(MatrixEntryRepository matrix, EvidenceTypeRepository types, EvidenceRecordRepository records,
                                 AuditLinkRepository links, CatalogLookup catalog, ChangeRecorder trace) {
        this.matrix = matrix;
        this.types = types;
        this.records = records;
        this.links = links;
        this.catalog = catalog;
        this.trace = trace;
    }

    /** Crea la copia de la organización a partir del borrador de partida si todavía no la tiene. */
    List<MatrixEntry> entries(Long orgId) {
        if (matrix.countByOrganizationId(orgId) == 0) {
            matrix.copyBaseTo(orgId);
        }
        return matrix.findByOrganizationIdOrderByTypeCodeAscStandardCodeAscControlRefAsc(orgId);
    }

    List<MatrixEntry> entriesFor(Long orgId, String standardCode) {
        entries(orgId);
        return matrix.findByOrganizationIdAndStandardCode(orgId, standardCode);
    }

    public MatrixDto get() {
        Long orgId = SecurityUtils.currentOrganizationId();
        List<MatrixEntry> all = entries(orgId);
        Map<String, EvidenceType> typeMap = types.findAll().stream().collect(Collectors.toMap(EvidenceType::getCode, Function.identity()));
        Map<String, Map<String, ControlPointView>> controls = new HashMap<>();
        Map<String, String> standardNames = new HashMap<>();
        for (String code : all.stream().map(MatrixEntry::getStandardCode).distinct().toList()) {
            controls.put(code, catalog.controlPointsOf(code).stream()
                    .collect(Collectors.toMap(ControlPointView::ref, Function.identity(), (a, b) -> a)));
            standardNames.put(code, catalog.findStandard(code).map(StandardView::name).orElse(code));
        }
        List<MatrixEntryDto> dtos = all.stream().map(e -> {
            ControlPointView cp = controls.getOrDefault(e.getStandardCode(), Map.of()).get(e.getControlRef());
            EvidenceType t = typeMap.get(e.getTypeCode());
            return new MatrixEntryDto(e.getId(), e.getTypeCode(), t == null ? e.getTypeCode() : t.getName(),
                    e.getStandardCode(), standardNames.get(e.getStandardCode()), e.getControlRef(),
                    cp == null ? null : cp.title(), e.getStatus());
        }).toList();
        return new MatrixDto(types.findAllByOrderBySortOrderAsc().stream().map(EvidenceRecordService::toTypeDto).toList(), dtos);
    }

    public MatrixEntryDto add(MatrixEntryRequest request) {
        Long orgId = SecurityUtils.currentOrganizationId();
        entries(orgId);
        EvidenceType type = types.findById(request.typeCode())
                .orElseThrow(() -> new BadRequestException("Tipo de evidencia no válido: " + request.typeCode()));
        StandardView standard = catalog.findStandard(request.standardCode())
                .orElseThrow(() -> new BadRequestException("Norma no encontrada: " + request.standardCode()));
        ControlPointView cp = catalog.controlPointsOf(standard.code()).stream()
                .filter(c -> c.ref().equals(request.controlRef())).findFirst()
                .orElseThrow(() -> new BadRequestException("La norma no tiene el punto de control «" + request.controlRef() + "»"));
        if (matrix.existsByOrganizationIdAndTypeCodeAndStandardCodeAndControlRef(orgId, type.getCode(), standard.code(), cp.ref())) {
            throw new ConflictException("Esa equivalencia ya existe");
        }
        MatrixEntry saved = matrix.save(new MatrixEntry(orgId, type.getCode(), standard.code(), cp.ref(), MatrixEntry.VALIDATED));
        trace.record(ChangeEntry.of(TraceEntity.EVIDENCE_MATRIX, saved.getId(), cp.ref(), null, TraceAction.CREATE,
                "Añadió a la matriz: «" + type.getName() + "» sirve para el punto " + cp.ref() + " de " + standard.name(),
                Changes.create().initial("typeCode", "Tipo de evidencia", type.getName())
                        .initial("controlRef", "Punto de control", standard.code() + " " + cp.ref()).list()));
        return new MatrixEntryDto(saved.getId(), type.getCode(), type.getName(), standard.code(), standard.name(), cp.ref(),
                cp.title(), saved.getStatus());
    }

    public void remove(Long id) {
        Long orgId = SecurityUtils.currentOrganizationId();
        MatrixEntry entry = require(orgId, id);
        List<EvidenceRecord> ofType = records.findByOrganizationIdOrderByIssuedOnDescIdDesc(orgId).stream()
                .filter(r -> r.getTypeCode().equals(entry.getTypeCode())).toList();
        Long cpId = catalog.controlPointsOf(entry.getStandardCode()).stream()
                .filter(c -> c.ref().equals(entry.getControlRef())).map(ControlPointView::id).findFirst().orElse(null);
        if (cpId != null && !ofType.isEmpty()) {
            long inUse = links.findByRecordIdIn(ofType.stream().map(EvidenceRecord::getId).toList()).stream()
                    .filter(l -> l.getControlPointId().equals(cpId)).count();
            if (inUse > 0) {
                throw new ConflictException("No se puede quitar: hay " + inUse + " vínculo(s) de auditorías que la usan.");
            }
        }
        matrix.delete(entry);
        trace.record(ChangeEntry.of(TraceEntity.EVIDENCE_MATRIX, id, entry.getControlRef(), null, TraceAction.DELETE,
                "Quitó de la matriz la equivalencia con el punto " + entry.getControlRef() + " de " + entry.getStandardCode(),
                Changes.create().track("controlRef", "Punto de control", entry.getStandardCode() + " " + entry.getControlRef(), null).list()));
    }

    public MatrixEntryDto validate(Long id, boolean validated) {
        Long orgId = SecurityUtils.currentOrganizationId();
        MatrixEntry entry = require(orgId, id);
        String before = entry.getStatus();
        entry.setStatus(validated ? MatrixEntry.VALIDATED : MatrixEntry.DRAFT);
        matrix.save(entry);
        if (!before.equals(entry.getStatus())) {
            trace.record(ChangeEntry.of(TraceEntity.EVIDENCE_MATRIX, id, entry.getControlRef(), null, TraceAction.UPDATE,
                    (validated ? "Validó" : "Volvió a borrador") + " la equivalencia con el punto " + entry.getControlRef()
                            + " de " + entry.getStandardCode(),
                    Changes.create().track("status", "Estado", statusLabel(before), statusLabel(entry.getStatus())).list()));
        }
        return get().entries().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private MatrixEntry require(Long orgId, Long id) {
        return matrix.findByIdAndOrganizationId(id, orgId).orElseThrow(() -> new NotFoundException("Equivalencia no encontrada: " + id));
    }

    private static String statusLabel(String status) {
        return MatrixEntry.VALIDATED.equals(status) ? "Validada" : "Borrador";
    }
}
