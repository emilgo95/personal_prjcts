package com.agriaudit.evidence.application;

import com.agriaudit.audit.AuditDtos.AuditDetailDto;
import com.agriaudit.audit.AuditDtos.AuditItemResultDto;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.StandardView;
import com.agriaudit.evidence.EvidenceDtos.CoverageDto;
import com.agriaudit.evidence.EvidenceDtos.CoverageItemDto;
import com.agriaudit.evidence.EvidenceDtos.CoverageRecordDto;
import com.agriaudit.evidence.EvidenceDtos.EvidenceMapDto;
import com.agriaudit.evidence.EvidenceDtos.MapEvidenceDto;
import com.agriaudit.evidence.EvidenceDtos.MapRowDto;
import com.agriaudit.evidence.EvidenceDtos.StandardRefDto;
import com.agriaudit.evidence.EvidenceDtos.TypeCoverageDto;
import com.agriaudit.evidence.domain.EvidenceRules;
import com.agriaudit.evidence.infrastructure.persistence.MatrixEntry;
import com.agriaudit.identity.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mapa de evidencias de una auditoría: para cada punto de control, qué lo justifica (evidencias reutilizables
 * vinculadas o por confirmar y adjuntos del checklist), en qué estado está y en qué puntos de otras normas vale la
 * misma evidencia según la matriz de equivalencias de la organización. Solo consulta: los vínculos se confirman con
 * {@link EvidenceCoverageService}.
 */
@Service
@Transactional
public class EvidenceMapService {

    public static final String COVERED = "COVERED";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String ALERT = "ALERT";
    public static final String SUGGESTED = "SUGGESTED";
    public static final String ATTACHMENT = "ATTACHMENT";
    public static final String MISSING = "MISSING";
    private static final List<String> STATES = List.of(COVERED, DUE_SOON, ALERT, SUGGESTED, ATTACHMENT, MISSING);

    private final AuditLookup audits;
    private final CatalogLookup catalog;
    private final EvidenceCoverageService coverage;
    private final EvidenceMatrixService matrix;

    public EvidenceMapService(AuditLookup audits, CatalogLookup catalog, EvidenceCoverageService coverage,
                              EvidenceMatrixService matrix) {
        this.audits = audits;
        this.catalog = catalog;
        this.coverage = coverage;
        this.matrix = matrix;
    }

    public EvidenceMapDto map(Long auditId) {
        Long orgId = SecurityUtils.currentOrganizationId();
        CoverageDto cov = coverage.coverage(auditId);
        AuditDetailDto audit = audits.get(auditId);
        List<AuditItemResultDto> results = audits.getResults(auditId);
        Map<Long, CoverageItemDto> coverageByPoint = cov.items().stream()
                .collect(Collectors.toMap(CoverageItemDto::controlPointId, Function.identity(), (a, b) -> a));

        // Por tipo de evidencia, los puntos de las demás normas donde vale (norma → referencias).
        Map<String, Map<String, TreeSet<String>>> elsewhereByType = new HashMap<>();
        for (MatrixEntry e : matrix.entries(orgId)) {
            if (!e.getStandardCode().equals(audit.standardCode())) {
                elsewhereByType.computeIfAbsent(e.getTypeCode(), k -> new HashMap<>())
                        .computeIfAbsent(e.getStandardCode(), k -> new TreeSet<>(REF_ORDER)).add(e.getControlRef());
            }
        }

        List<MapRowDto> rows = new ArrayList<>();
        Map<String, Integer> summary = new LinkedHashMap<>();
        STATES.forEach(s -> summary.put(s, 0));
        int attachmentsTotal = 0;
        TreeSet<String> otherCodes = new TreeSet<>();
        for (AuditItemResultDto r : results) {
            CoverageItemDto item = coverageByPoint.get(r.controlPointId());
            int attachments = r.attachments() == null ? 0 : r.attachments().size();
            attachmentsTotal += attachments;
            if (item == null && attachments == 0) {
                continue; // ni la matriz pide evidencia para este punto ni tiene adjuntos
            }
            List<TypeCoverageDto> types = item == null ? List.of() : item.types();
            Map<String, List<String>> equivalences = equivalences(types, elsewhereByType);
            otherCodes.addAll(equivalences.keySet());
            List<MapEvidenceDto> evidence = new ArrayList<>();
            String state = state(types, attachments, evidence);
            summary.merge(state, 1, Integer::sum);
            rows.add(new MapRowDto(r.controlPointId(), r.ref(), r.title(), r.chapterCode(), r.level(), r.compliance(),
                    types.stream().map(TypeCoverageDto::typeName).toList(), evidence, attachments, equivalences, state));
        }
        summary.put("POINTS", results.size());
        summary.put("ATTACHMENTS", attachmentsTotal);

        List<StandardRefDto> others = otherCodes.stream()
                .map(code -> new StandardRefDto(code, catalog.findStandard(code).map(StandardView::name).orElse(code)))
                .sorted(Comparator.comparing(StandardRefDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new EvidenceMapDto(auditId, cov.farmId(), audit.standardCode(), audit.standardName(), cov.locked(),
                others, rows, summary);
    }

    /** Puntos de las demás normas para los que valen los tipos de evidencia de este punto. */
    private static Map<String, List<String>> equivalences(List<TypeCoverageDto> types,
                                                         Map<String, Map<String, TreeSet<String>>> elsewhereByType) {
        Map<String, TreeSet<String>> merged = new TreeMap<>();
        for (TypeCoverageDto t : types) {
            elsewhereByType.getOrDefault(t.typeCode(), Map.of())
                    .forEach((code, refs) -> merged.computeIfAbsent(code, k -> new TreeSet<>(REF_ORDER)).addAll(refs));
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        merged.forEach((code, refs) -> out.put(code, List.copyOf(refs)));
        return out;
    }

    /**
     * Estado del punto y, en {@code evidence}, lo que se muestra: las evidencias vinculadas; si no hay ninguna, las
     * candidatas que se pueden confirmar (la más reciente de cada tipo); y si tampoco, la última registrada aunque ya
     * no sirva (vencida o desfavorable), para saber qué hay que renovar.
     */
    private static String state(List<TypeCoverageDto> types, int attachments, List<MapEvidenceDto> evidence) {
        boolean alert = false;
        boolean dueSoon = false;
        for (TypeCoverageDto t : types) {
            for (CoverageRecordDto rec : t.records()) {
                if (rec.linkId() != null) {
                    evidence.add(toEvidence(rec, t, true));
                    alert |= !rec.linkable();
                    dueSoon |= EvidenceRules.DUE_SOON.equals(rec.status());
                }
            }
        }
        if (!evidence.isEmpty()) {
            return alert ? ALERT : dueSoon ? DUE_SOON : COVERED;
        }
        for (TypeCoverageDto t : types) {
            t.records().stream().filter(CoverageRecordDto::linkable).findFirst()
                    .ifPresent(rec -> evidence.add(toEvidence(rec, t, false)));
        }
        if (!evidence.isEmpty()) {
            return SUGGESTED;
        }
        // Ninguna sirve: se enseña la más reciente de cada tipo (vencida o desfavorable) para saber qué renovar.
        for (TypeCoverageDto t : types) {
            t.records().stream().findFirst().ifPresent(rec -> evidence.add(toEvidence(rec, t, false)));
        }
        return attachments > 0 ? ATTACHMENT : MISSING;
    }

    private static MapEvidenceDto toEvidence(CoverageRecordDto rec, TypeCoverageDto type, boolean linked) {
        return new MapEvidenceDto(rec.id(), rec.title(), type.typeName(), rec.status(), rec.outcome(),
                rec.validUntil(), linked);
    }

    /** Orden natural de referencias («AF 2.10» después de «AF 2.9»). */
    private static final Comparator<String> REF_ORDER = (a, b) -> {
        String[] pa = a.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        String[] pb = b.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            int c = pa[i].chars().allMatch(Character::isDigit) && pb[i].chars().allMatch(Character::isDigit)
                    && !pa[i].isEmpty() && !pb[i].isEmpty()
                    ? Long.compare(Long.parseLong(pa[i]), Long.parseLong(pb[i]))
                    : pa[i].compareToIgnoreCase(pb[i]);
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    };
}
