package com.agriaudit.ai.infrastructure.web;

import com.agriaudit.ai.application.EvidenceAnalysisService;
import com.agriaudit.ai.application.EvidenceAnalysisDtos.EvidenceAnalysis;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EvidenceAnalysisController {

    private final EvidenceAnalysisService analysis;

    public EvidenceAnalysisController(EvidenceAnalysisService analysis) {
        this.analysis = analysis;
    }

    /**
     * Reconoce el texto de una evidencia adjunta y devuelve propuestas de nota,
     * cumplimiento y no conformidades. NO guarda nada.
     */
    @PostMapping("/audits/{auditId}/evidence/{attachmentId}/analyze")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public EvidenceAnalysis analyze(@PathVariable Long auditId,
                                    @PathVariable Long attachmentId,
                                    @RequestParam(value = "controlPointId", required = false) Long controlPointId) {
        return analysis.analyze(auditId, attachmentId, controlPointId);
    }
}
