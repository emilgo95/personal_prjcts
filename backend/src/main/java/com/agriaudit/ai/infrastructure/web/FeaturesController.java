package com.agriaudit.ai.infrastructure.web;

import com.agriaudit.ai.application.EvidenceAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Indica al frontal qué funciones opcionales están disponibles en este entorno. */
@RestController
@RequestMapping("/api/features")
public class FeaturesController {

    private final EvidenceAnalysisService analysis;

    public FeaturesController(EvidenceAnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping
    public Map<String, Object> features() {
        return Map.of("evidenceOcr", analysis.isEnabled());
    }
}
