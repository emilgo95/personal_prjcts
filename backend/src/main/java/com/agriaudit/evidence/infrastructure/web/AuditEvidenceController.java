package com.agriaudit.evidence.infrastructure.web;

import com.agriaudit.evidence.EvidenceDtos.CoverageDto;
import com.agriaudit.evidence.EvidenceDtos.LinkRequest;
import com.agriaudit.evidence.application.EvidenceCoverageService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Evidencias reutilizables vistas desde una auditoría: qué cubre cada punto y los vínculos confirmados. */
@RestController
@RequestMapping("/api/audits/{auditId}")
public class AuditEvidenceController {

    private final EvidenceCoverageService coverage;

    public AuditEvidenceController(EvidenceCoverageService coverage) {
        this.coverage = coverage;
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
