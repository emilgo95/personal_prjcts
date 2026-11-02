package com.agriaudit.evidence.infrastructure.web;

import com.agriaudit.evidence.EvidenceDtos.CoverageDto;
import com.agriaudit.evidence.EvidenceDtos.EvidenceMapDto;
import com.agriaudit.evidence.EvidenceDtos.LinkRequest;
import com.agriaudit.evidence.application.EvidenceCoverageService;
import com.agriaudit.evidence.application.EvidenceMapService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Evidencias reutilizables vistas desde una auditoría: qué cubre cada punto y los vínculos confirmados. */
@RestController
@RequestMapping("/api/audits/{auditId}")
public class AuditEvidenceController {

    private final EvidenceCoverageService coverage;
    private final EvidenceMapService map;

    public AuditEvidenceController(EvidenceCoverageService coverage, EvidenceMapService map) {
        this.coverage = coverage;
        this.map = map;
    }

    /** Mapa de evidencias de la auditoría: qué justifica cada punto y en qué puntos de otras normas vale. */
    @GetMapping("/evidence-map")
    public EvidenceMapDto map(@PathVariable Long auditId) {
        return map.map(auditId);
    }

    @GetMapping("/evidence-coverage")
    public CoverageDto coverage(@PathVariable Long auditId) {
        return coverage.coverage(auditId);
    }

    @PostMapping("/evidence-links")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public CoverageDto link(@PathVariable Long auditId, @Valid @RequestBody LinkRequest request) {
        return coverage.link(auditId, request);
    }

    @DeleteMapping("/evidence-links/{linkId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public CoverageDto unlink(@PathVariable Long auditId, @PathVariable Long linkId) {
        return coverage.unlink(auditId, linkId);
    }
}
