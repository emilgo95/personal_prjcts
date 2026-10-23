package com.agriaudit.audit.infrastructure.web;

import com.agriaudit.audit.application.AuditService;
import com.agriaudit.audit.AuditDtos.*;
import com.agriaudit.audit.ScoreDtos.ScoreResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public AuditPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return auditService.page(page, size);
    }

    @GetMapping("/{id}")
    public AuditDetailDto get(@PathVariable Long id) {
        return auditService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AuditDetailDto create(@Valid @RequestBody AuditRequest request) {
        return auditService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AuditDetailDto update(@PathVariable Long id, @Valid @RequestBody AuditRequest request) {
        return auditService.update(id, request);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AuditDetailDto transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        return auditService.transition(id, request.status());
    }

    @GetMapping("/{id}/results")
    public List<AuditItemResultDto> results(@PathVariable Long id) {
        return auditService.getResults(id);
    }

    @PutMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<AuditItemResultDto> updateResults(@PathVariable Long id,
                                                  @Valid @RequestBody ResultsUpdateRequest request) {
        return auditService.updateResults(id, request);
    }

    @GetMapping("/{id}/score")
    public ScoreResult score(@PathVariable Long id) {
        return auditService.score(id);
    }
}
