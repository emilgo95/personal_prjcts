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
import com.agriaudit.catalog.infrastructure.persistence.HiddenStandardRepository;
import com.agriaudit.identity.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
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
    private final HiddenStandardRepository hidden;

    public CatalogService(AuditStandardRepository standards,
                          ControlPointRepository controlPoints,
                          RegulationRepository regulations,
                          HiddenStandardRepository hidden) {
        this.hidden = hidden;
        this.standards = standards;
        this.controlPoints = controlPoints;
        this.regulations = regulations;
    }

    /** Estándares que usa la organización: sin los ocultos ni los archivados. */
    public List<StandardSummaryDto> listStandards() {
        return listStandards(false);
    }

    /**
     * Estándares comunes y propios de la organización. Con {@code all} se incluyen los ocultos y los archivados
     * (para gestionarlos); sin él, solo los que se ofrecen al crear auditorías.
     */
    public List<StandardSummaryDto> listStandards(boolean all) {
        Long orgId = SecurityUtils.currentOrganizationIdOrNull();
        java.util.Set<Long> hiddenIds = hidden.hiddenBy(orgId);
        List<StandardSummaryDto> result = new ArrayList<>();
        for (AuditStandard s : standards.findVisible(orgId)) {
            boolean isHidden = hiddenIds.contains(s.getId());
            if (!all && (isHidden || s.isArchived())) {
                continue;
            }
            List<ControlPoint> cps = controlPoints.findByStandardCode(s.getCode(), orgId);
            long chapters = cps.stream().map(cp -> cp.getChapter().getId()).distinct().count();
            result.add(new StandardSummaryDto(
                    s.getId(), s.getCode(), s.getVersion(), s.getName(), s.getOwnerBody(),
                    s.getCategory().name(), s.getDescription(), s.getOfficialUrl(),
                    (int) chapters, cps.size(), s.isCustom(), isHidden, s.isArchived()));
        }
        return result;
    }

    public StandardDetailDto getStandard(String code) {
        Long orgId = SecurityUtils.currentOrganizationIdOrNull();
        AuditStandard s = standards.findVisibleByCode(code, orgId)
                .orElseThrow(() -> new NotFoundException("Estándar no encontrado: " + code));
        List<ControlPoint> cps = controlPoints.findByStandardCode(code, orgId);

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
                s.getCategory().name(), s.getDescription(), s.getScoringRule(), s.getOfficialUrl(), chapterDtos,
                closureRule(s));
    }

    public List<ControlPointDto> listControlPoints(String code) {
        Long orgId = SecurityUtils.currentOrganizationIdOrNull();
        if (standards.findVisibleByCode(code, orgId).isEmpty()) {
            throw new NotFoundException("Estándar no encontrado: " + code);
        }
        return controlPoints.findByStandardCode(code, orgId).stream().map(this::toControlPointDto).toList();
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
    public Optional<StandardView> findStandard(String code) {
        return standards.findVisibleByCode(code, SecurityUtils.currentOrganizationIdOrNull())
                .map(CatalogService::toView);
    }

    @Override
    public Optional<StandardView> findAvailableStandard(String code) {
        Long orgId = SecurityUtils.currentOrganizationIdOrNull();
        return standards.findVisibleByCode(code, orgId)
                .filter(s -> s.isActive() && !s.isArchived() && !hidden.hiddenBy(orgId).contains(s.getId()))
                .map(CatalogService::toView);
    }

    @Override
    public Optional<StandardView> findStandard(Long id) {
        return standards.findById(id).map(CatalogService::toView);
    }

    @Override
    public List<ControlPointView> controlPointsOf(String standardCode) {
        return controlPoints.findByStandardCode(standardCode, SecurityUtils.currentOrganizationIdOrNull()).stream()
                .map(CatalogService::toView).toList();
    }

    @Override
    public Optional<ControlPointView> findControlPoint(Long id) {
        return controlPoints.findWithChapterById(id).map(CatalogService::toView);
    }

    @Override
    public Map<Long, ControlPointView> findControlPoints(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ControlPointView> byId = new LinkedHashMap<>();
        for (ControlPoint cp : controlPoints.findWithChapterByIdIn(ids)) {
            byId.put(cp.getId(), toView(cp));
        }
        return byId;
    }

    private static StandardView toView(AuditStandard s) {
        return new StandardView(s.getId(), s.getCode(), s.getVersion(), s.getName(), s.getScoringRule(),
                closureRule(s));
    }

    private static ClosureRule closureRule(AuditStandard s) {
        return new ClosureRule(s.getClosureDaysCritical(), s.getClosureDaysMajor(), s.getClosureDaysMinor(),
                s.getClosureDaysObservation());
    }

    private static ControlPointView toView(ControlPoint cp) {
        StandardChapter ch = cp.getChapter();
        return new ControlPointView(cp.getId(), cp.getRef(), cp.getTitle(), cp.getDescription(), cp.getGuidance(),
                cp.getLevel(), cp.getApplicableScopes(), ch.getCode(), ch.getTitle(),
                ch.getStandard().getId(), ch.getStandard().getCode());
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
