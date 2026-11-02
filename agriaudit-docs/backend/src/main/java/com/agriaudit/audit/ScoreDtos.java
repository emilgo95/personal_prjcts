package com.agriaudit.audit;

import java.util.List;

public final class ScoreDtos {

    private ScoreDtos() {
    }

    public record LevelScore(
            String level,
            int total,
            int compliant,
            int nonCompliant,
            int notApplicable,
            int notAssessed,
            Double compliancePct) {
    }

    public record GroupScore(
            String code,
            String title,
            int total,
            int compliant,
            int nonCompliant,
            int notApplicable,
            int notAssessed,
            double compliancePct) {
    }

    public record ScoreResult(
            String standardCode,
            String standardName,
            String scoringType,
            boolean reportOnly,
            String overallResult,
            Double majorMustMinPct,
            Double minorMustMinPct,
            List<LevelScore> levelBreakdown,
            List<GroupScore> chapterBreakdown,
            List<GroupScore> pillarBreakdown,
            int totalControlPoints,
            int assessedCount,
            int progressPct,
            List<String> notes) {
    }
}
