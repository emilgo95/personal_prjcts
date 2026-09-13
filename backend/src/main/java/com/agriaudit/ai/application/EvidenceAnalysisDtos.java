package com.agriaudit.ai.application;

import java.util.List;

public final class EvidenceAnalysisDtos {

    private EvidenceAnalysisDtos() {
    }

    public record SuggestedFinding(
            String title,
            String description,
            String classification) {
    }

    public record EvidenceAnalysis(
            Long attachmentId,
            String fileName,
            String engine,
            int pages,
            boolean truncated,
            double meanConfidence,
            String extractedText,
            String suggestedEvidenceNote,
            String suggestedCompliance,
            String complianceRationale,
            List<SuggestedFinding> suggestedFindings,
            List<String> warnings) {
    }
}
