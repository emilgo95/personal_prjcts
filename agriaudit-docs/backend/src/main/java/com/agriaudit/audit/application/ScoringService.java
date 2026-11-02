package com.agriaudit.audit.application;

import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.domain.AuditItemResult;
import com.agriaudit.audit.domain.ComplianceResult;
import com.agriaudit.audit.infrastructure.persistence.AuditItemResultRepository;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.catalog.StandardView;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agriaudit.audit.ScoreDtos.GroupScore;
import com.agriaudit.audit.ScoreDtos.LevelScore;
import com.agriaudit.audit.ScoreDtos.ScoreResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ScoringService {

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private final CatalogLookup catalog;
    private final AuditItemResultRepository results;
    private final ObjectMapper objectMapper;

    public ScoringService(CatalogLookup catalog,
                          AuditItemResultRepository results,
                          ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.results = results;
        this.objectMapper = objectMapper;
    }

    public ScoreResult score(Audit audit) {
        StandardView standard = catalog.findStandard(audit.getStandardId())
                .orElseThrow(() -> new IllegalStateException("Estándar inexistente: " + audit.getStandardId()));
        String standardCode = standard.code();
        List<ControlPointView> cps = catalog.controlPointsOf(standardCode);
        Map<Long, ComplianceResult> byCp = new LinkedHashMap<>();
        for (AuditItemResult r : results.findByAuditId(audit.getId())) {
            byCp.put(r.getControlPointId(), r.getCompliance());
        }

        JsonNode rule = parseRule(standard.scoringRule());
        boolean invalidRule = rule == null;
        if (invalidRule) {
            rule = objectMapper.createObjectNode().put("type", "INVALID");
        }
        String type = rule.path("type").asText("MAJOR_MINOR");
        boolean reportOnly = rule.path("reportOnly").asBoolean(false);
        Double majorMin = rule.has("majorMustMinPct") ? rule.get("majorMustMinPct").asDouble() : null;
        Double minorMin = rule.has("minorMustMinPct") ? rule.get("minorMustMinPct").asDouble() : null;

        // ---- desglose por nivel ----
        Map<String, int[]> levelCounts = new LinkedHashMap<>();
        // ---- desglose por capítulo ----
        Map<String, GroupAcc> chapterAcc = new LinkedHashMap<>();
        // ---- desglose por pilar (SMETA) ----
        Map<String, GroupAcc> pillarAcc = new LinkedHashMap<>();

        int assessed = 0;
        for (ControlPointView cp : cps) {
            ComplianceResult c = byCp.getOrDefault(cp.id(), ComplianceResult.NOT_ASSESSED);
            if (c != ComplianceResult.NOT_ASSESSED) {
                assessed++;
            }
            int[] lc = levelCounts.computeIfAbsent(cp.level().name(), k -> new int[4]);
            bump(lc, c);

            String chKey = cp.chapterCode();
            chapterAcc.computeIfAbsent(chKey, k -> new GroupAcc(chKey, cp.chapterTitle())).bump(c);

            String pillar = extractPillar(cp.applicableScopes());
            if (pillar != null) {
                pillarAcc.computeIfAbsent(pillar, k -> new GroupAcc(pillar, pillarTitle(pillar))).bump(c);
            }
        }

        List<LevelScore> levelBreakdown = new ArrayList<>();
        levelCounts.forEach((level, a) -> {
            int total = a[0] + a[1] + a[2] + a[3];
            int denom = a[0] + a[1];
            Double pct = denom > 0 ? round1(100.0 * a[0] / denom) : null;
            levelBreakdown.add(new LevelScore(level, total, a[0], a[1], a[2], a[3], pct));
        });

        List<GroupScore> chapterBreakdown = chapterAcc.values().stream().map(GroupAcc::toScore).toList();
        List<GroupScore> pillarBreakdown = pillarAcc.isEmpty()
                ? List.of()
                : pillarAcc.values().stream().map(GroupAcc::toScore).toList();

        int total = cps.size();
        int progress = total == 0 ? 0 : Math.round(100f * assessed / total);

        List<String> notes = new ArrayList<>();
        String overall;

        if (invalidRule) {
            // Nunca se da por «apto» lo que no se puede evaluar: una regla ilegible deja el resultado sin concluir.
            overall = "PENDING";
            notes.add("La regla de puntuación del estándar no es válida: no se puede calcular el resultado.");
        } else if ("MAJOR_MINOR".equals(type) && majorMin == null && minorMin == null) {
            overall = "PENDING";
            notes.add("La regla de puntuación del estándar no define porcentajes mínimos: no se puede calcular el resultado.");
        } else if ("MAJOR_MINOR".equals(type)) {
            int[] major = levelCounts.getOrDefault("MAJOR_MUST", new int[4]);
            int[] minor = levelCounts.getOrDefault("MINOR_MUST", new int[4]);
            int majorApplicable = major[0] + major[1] + major[3]; // excluye N.A.
            int minorApplicable = minor[0] + minor[1] + minor[3];
            boolean pending = major[3] > 0 || minor[3] > 0;
            double majorPct = (major[0] + major[1]) == 0 ? 100.0 : 100.0 * major[0] / (major[0] + major[1]);
            double minorPct = (minor[0] + minor[1]) == 0 ? 100.0 : 100.0 * minor[0] / (minor[0] + minor[1]);
            if (pending) {
                overall = "PENDING";
                notes.add("Quedan puntos obligatorios sin evaluar: la evaluación no es concluyente.");
            } else {
                boolean pass = (majorMin == null || majorPct >= majorMin)
                        && (minorMin == null || minorPct >= minorMin);
                overall = pass ? "PASS" : "FAIL";
            }
            notes.add(String.format(ES, "Obligaciones mayores cumplidas: %.1f %% (mínimo %s %%).", majorPct,
                    majorMin == null ? "-" : majorMin.intValue()));
            notes.add(String.format(ES, "Obligaciones menores cumplidas: %.1f %% (mínimo %s %%).", minorPct,
                    minorMin == null ? "-" : minorMin.intValue()));
            if (majorApplicable + minorApplicable == 0) {
                notes.add("No hay puntos obligatorios aplicables en este alcance.");
            }
        } else if (reportOnly || "LEVEL_COMPLETION".equals(type) || "PILLAR".equals(type)) {
            overall = "REPORT_ONLY";
            notes.add("Este estándar genera un informe de evaluación; no produce un resultado de aprobado/suspenso automático.");
        } else {
            overall = "PENDING";
        }

        notes.add("Resultado orientativo: se calcula sobre el catálogo de demostración de la aplicación (" + total
                + " puntos de control), no sobre el texto íntegro de la norma.");

        return new ScoreResult(
                standardCode,
                standard.name(),
                type,
                reportOnly || "REPORT_ONLY".equals(overall),
                overall,
                majorMin,
                minorMin,
                levelBreakdown,
                chapterBreakdown,
                pillarBreakdown,
                total,
                assessed,
                progress,
                notes);
    }

    /** Regla de puntuación del estándar; {@code null} si falta o no es un objeto JSON válido. */
    private JsonNode parseRule(String json) {
        try {
            if (json == null || json.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** cuenta [compliant, nonCompliant, notApplicable, notAssessed] */
    private void bump(int[] a, ComplianceResult c) {
        switch (c) {
            case COMPLIANT -> a[0]++;
            case NON_COMPLIANT -> a[1]++;
            case NOT_APPLICABLE -> a[2]++;
            case NOT_ASSESSED -> a[3]++;
        }
    }

    private static String extractPillar(String applicableScopes) {
        if (applicableScopes == null) return null;
        return switch (applicableScopes) {
            case "LABOUR", "HEALTH_SAFETY", "ENVIRONMENT", "BUSINESS_ETHICS", "MANAGEMENT" -> applicableScopes;
            default -> null;
        };
    }

    private static String pillarTitle(String pillar) {
        return switch (pillar) {
            case "LABOUR" -> "Normas laborales";
            case "HEALTH_SAFETY" -> "Salud y seguridad";
            case "ENVIRONMENT" -> "Medio ambiente";
            case "BUSINESS_ETHICS" -> "Ética empresarial";
            case "MANAGEMENT" -> "Gestión del sistema";
            default -> pillar;
        };
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static final class GroupAcc {
        final String code;
        final String title;
        int compliant, nonCompliant, notApplicable, notAssessed;

        GroupAcc(String code, String title) {
            this.code = code;
            this.title = title;
        }

        void bump(ComplianceResult c) {
            switch (c) {
                case COMPLIANT -> compliant++;
                case NON_COMPLIANT -> nonCompliant++;
                case NOT_APPLICABLE -> notApplicable++;
                case NOT_ASSESSED -> notAssessed++;
            }
        }

        GroupScore toScore() {
            int total = compliant + nonCompliant + notApplicable + notAssessed;
            int denom = compliant + nonCompliant;
            double pct = denom == 0 ? 0.0 : Math.round(1000.0 * compliant / denom) / 10.0;
            return new GroupScore(code, title, total, compliant, nonCompliant, notApplicable, notAssessed, pct);
        }
    }
}
