package com.agriaudit.findings.infrastructure.web;

import com.agriaudit.findings.application.FindingService;
import com.agriaudit.findings.FindingDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audits/{auditId}/findings")
public class FindingController {

    private final FindingService findingService;

    public FindingController(FindingService findingService) {
        this.findingService = findingService;
    }

    @GetMapping
    public List<FindingDto> list(@PathVariable Long auditId) {
        return findingService.listByAudit(auditId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public FindingDto create(@PathVariable Long auditId, @Valid @RequestBody FindingRequest request) {
        return findingService.create(auditId, request);
    }

    @PutMapping("/{findingId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public FindingDto update(@PathVariable Long auditId, @PathVariable Long findingId,
                             @Valid @RequestBody FindingRequest request) {
        return findingService.update(auditId, findingId, request);
    }

    @DeleteMapping("/{findingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public void delete(@PathVariable Long auditId, @PathVariable Long findingId) {
        findingService.delete(auditId, findingId);
    }

    @PostMapping("/{findingId}/corrective-actions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public CorrectiveActionDto addCorrectiveAction(@PathVariable Long auditId, @PathVariable Long findingId,
                                                   @Valid @RequestBody CorrectiveActionRequest request) {
        return findingService.addCorrectiveAction(auditId, findingId, request);
    }

    @PutMapping("/{findingId}/corrective-actions/{caId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public CorrectiveActionDto updateCorrectiveAction(@PathVariable Long auditId, @PathVariable Long findingId,
                                                      @PathVariable Long caId,
                                                      @Valid @RequestBody CorrectiveActionRequest request) {
        return findingService.updateCorrectiveAction(auditId, findingId, caId, request);
    }

    @DeleteMapping("/{findingId}/corrective-actions/{caId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public void deleteCorrectiveAction(@PathVariable Long auditId, @PathVariable Long findingId,
                                       @PathVariable Long caId) {
        findingService.deleteCorrectiveAction(auditId, findingId, caId);
    }

    @PostMapping("/{findingId}/corrective-actions/{caId}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public CorrectiveActionDto verifyCorrectiveAction(@PathVariable Long auditId, @PathVariable Long findingId,
                                                      @PathVariable Long caId) {
        return findingService.verifyCorrectiveAction(auditId, findingId, caId);
    }

    @PostMapping("/{findingId}/close")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public FindingDto close(@PathVariable Long auditId, @PathVariable Long findingId) {
        return findingService.closeFinding(auditId, findingId);
    }
}
