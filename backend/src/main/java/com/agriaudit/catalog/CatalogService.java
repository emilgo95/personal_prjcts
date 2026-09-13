package com.agriaudit.catalog;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.catalog.domain.AuditStandard;
import com.agriaudit.catalog.domain.ControlPoint;
import com.agriaudit.catalog.domain.Regulation;
import com.agriaudit.catalog.domain.StandardChapter;
import com.agriaudit.catalog.infrastructure.persistence.AuditStandardRepository;
import com.agriaudit.catalog.infrastructure.persistence.ControlPointRepository;
import com.agriaudit.catalog.infrastructure.persistence.RegulationRepository;
import com.agriaudit.catalog.CatalogDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CatalogService implements CatalogLookup {

    private final AuditStandardRepository standards;
    private final ControlPointRepository controlPoints;
    private final RegulationRepository regulations;

    public CatalogService(AuditStandardRepository standards,
                          ControlPointRepository controlPoints,
                          RegulationRepository regulations) {
        this.standards = standards;
        this.controlPoints = controlPoints;
        this.regulations = regulations;
    }

    public List<StandardSummaryDto> listStandards() {
        List<StandardSummaryDto> result = new ArrayList<>();
        for (AuditStandard s : standards.findByActiveTrueOrderByName()) {
            List<ControlPoint> cps = controlPoints.findByStandardCode(s.getCode());
            long chapters = cps.stream().map(cp -> cp.getChapter().getId()).distinct().count();
            result.add(new StandardSummaryDto(
                    s.getId(), s.getCode(), s.getVersion(), s.getName(), s.getOwnerBody(),
                    s.getCategory().name(), s.getDescription(), s.getOfficialUrl(),
                    (int) chapters, cps.size()));
        }
        return result;
    }

    public StandardDetailDto getStandard(String code) {
        AuditStandard s = standards.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Estándar no encontrado: " + code));
        List<ControlPoint> cps = controlPoints.findByStandardCode(code);

        Map<Long, List<ControlPoint>> byChapter = new LinkedHashMap<>();
        for (ControlPoint cp : cps) {
            byChapter.computeIfAbsent(cp.getChapter().getId(), k -> new ArrayList<>()).add(cp);
        }

        List<ChapterDto> chapterDtos = new ArrayList<>();
        byChapter.values().stream()
                .filter(list -> !list.isEmpty())
                .sorted(Comparator.comparingInt(a -> a.get(0).getChapter().getSortOrder()))
                .forEach(list -> {
                    StandardChapter ch = list.get(0).getChapter();
                    List<ControlPointDto> points = list.stream().map(this::toControlPointDto).toList();
                    chapterDtos.add(new ChapterDto(ch.getId(), ch.getCode(), ch.getTitle(),
                            ch.getDescription(), ch.getSortOrder(), points));
                });

        return new StandardDetailDto(s.getId(), s.getCode(), s.getVersion(), s.getName(), s.getOwnerBody(),
                s.getCategory().name(), s.getDescription(), s.getScoringRule(), s.getOfficialUrl(), chapterDtos);
    }

    public List<ControlPointDto> listControlPoints(String code) {
        if (standards.findByCode(code).isEmpty()) {
            throw new NotFoundException("Estándar no encontrado: " + code);
        }
        return controlPoints.findByStandardCode(code).stream().map(this::toControlPointDto).toList();
    }

    public List<RegulationDto> listRegulations() {
        return regulations.findAllByOrderByJurisdictionAscTitleAsc().stream().map(this::toRegulationDto).toList();
    }

    public RegulationDto getRegulation(String code) {
        return regulations.findByCode(code).map(this::toRegulationDto)
                .orElseThrow(() -> new NotFoundException("Normativa no encontrada: " + code));
    }

    // ---- CatalogLookup ----

    @Override
    public Optional<AuditStandard> findStandard(String code) {
        return standards.findByCode(code);
    }

    @Override
    public List<ControlPoint> controlPointsOf(String standardCode) {
        return controlPoints.findByStandardCode(standardCode);
    }

    @Override
    public Optional<ControlPoint> findControlPoint(Long id) {
        return controlPoints.findById(id);
    }

    private ControlPointDto toControlPointDto(ControlPoint cp) {
        List<RegulationRefDto> regs = cp.getRegulations().stream()
                .map(r -> new RegulationRefDto(r.getCode(), r.getTitle(), r.getJurisdiction(), r.getSourceUrl()))
                .toList();
        return new ControlPointDto(
                cp.getId(), cp.getRef(), cp.getTitle(), cp.getDescription(), cp.getLevel().name(),
                cp.getGuidance(), cp.getEvidenceExamples(), cp.getApplicableScopes(),
                cp.getChapter().getCode(), cp.getChapter().getTitle(), regs);
    }

    private RegulationDto toRegulationDto(Regulation r) {
        return new RegulationDto(r.getId(), r.getCode(), r.getTitle(), r.getJurisdiction(),
                r.getScopeArea(), r.getSummary(), r.getKeyObligations(), r.getSourceUrl());
    }
}
