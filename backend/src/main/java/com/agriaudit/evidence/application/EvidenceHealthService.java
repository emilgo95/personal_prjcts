package com.agriaudit.evidence.application;

import com.agriaudit.evidence.EvidenceDtos.FarmHealthDto;
import com.agriaudit.evidence.EvidenceDtos.HealthCellDto;
import com.agriaudit.evidence.EvidenceDtos.HealthDto;
import com.agriaudit.evidence.EvidenceDtos.TypeDto;
import com.agriaudit.evidence.domain.EvidenceRules;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecord;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceType;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceRecordRepository;
import com.agriaudit.evidence.infrastructure.persistence.EvidenceTypeRepository;
import com.agriaudit.fieldbook.FarmDirectory;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.identity.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Panel «salud del sistema de gestión»: estado de cada tipo de evidencia en cada explotación, independiente de la norma. */
@Service
@Transactional
public class EvidenceHealthService {

    private final EvidenceMatrixService matrix;
    private final EvidenceTypeRepository types;
    private final EvidenceRecordRepository records;
    private final FarmDirectory farms;
    private final int windowDays;

    public EvidenceHealthService(EvidenceMatrixService matrix, EvidenceTypeRepository types, EvidenceRecordRepository records,
                                 FarmDirectory farms, @Value("${evidence.renewal-window-days:30}") int windowDays) {
        this.matrix = matrix;
        this.types = types;
        this.records = records;
        this.farms = farms;
        this.windowDays = windowDays;
    }

    public HealthDto health() {
        Long orgId = SecurityUtils.currentOrganizationId();
        // Solo los tipos que la matriz de la organización usa en algún punto.
        Set<String> used = matrix.entries(orgId).stream().map(e -> e.getTypeCode()).collect(Collectors.toSet());
        List<EvidenceType> shownTypes = types.findAllByOrderBySortOrderAsc().stream().filter(t -> used.contains(t.getCode())).toList();
        Map<String, List<EvidenceRecord>> byFarmAndType = records.findByOrganizationIdOrderByIssuedOnDescIdDesc(orgId).stream()
                .collect(Collectors.groupingBy(r -> r.getFarmId() + "|" + r.getTypeCode()));
        LocalDate today = LocalDate.now();
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (String s : List.of(EvidenceRules.CURRENT, EvidenceRules.DUE_SOON, EvidenceRules.EXPIRED, EvidenceRules.UNFAVORABLE,
                EvidenceRules.MISSING)) {
            summary.put(s, 0);
        }
        List<FarmHealthDto> rows = new java.util.ArrayList<>();
        for (FarmDto farm : farms.listFarms(orgId)) {
            Map<String, HealthCellDto> cells = new LinkedHashMap<>();
            for (EvidenceType t : shownTypes) {
                EvidenceRules.Health h = EvidenceRules.health(byFarmAndType.getOrDefault(farm.id() + "|" + t.getCode(), List.<EvidenceRecord>of())
                        .stream().map(EvidenceRecord::facts).toList(), today, windowDays);
                cells.put(t.getCode(), new HealthCellDto(h.status(), h.recordId(), h.validUntil()));
                summary.merge(h.status(), 1, Integer::sum);
            }
            rows.add(new FarmHealthDto(farm.id(), farm.legalName(), cells));
        }
        List<TypeDto> typeDtos = shownTypes.stream().map(t -> new TypeDto(t.getCode(), t.getName(), t.getDescription(),
                t.getPersonalDataNotice())).toList();
        return new HealthDto(typeDtos, rows, summary, windowDays);
    }
}
