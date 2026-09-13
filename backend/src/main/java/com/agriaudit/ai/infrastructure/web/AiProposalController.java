package com.agriaudit.ai.infrastructure.web;

import com.agriaudit.ai.application.AiProposalService;
import com.agriaudit.ai.domain.AiProposal;
import com.agriaudit.ai.domain.AiProposalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Revisión humana de las propuestas de IA (RS-IA-01). No hay ningún endpoint que dispare una llamada
 * genérica al modelo: las propuestas las crean los casos de uso de cada asistente.
 */
@RestController
@RequestMapping("/api/ai/proposals")
public class AiProposalController {

    private final AiProposalService proposals;

    public AiProposalController(AiProposalService proposals) {
        this.proposals = proposals;
    }

    @GetMapping
    public ProposalPage list(@RequestParam(defaultValue = "PENDING") AiProposalStatus status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        List<ProposalDto> items = proposals.list(status, page, size).stream().map(ProposalDto::of).toList();
        return new ProposalPage(items, page, Math.min(Math.max(size, 1), 100), proposals.count(status));
    }

    @GetMapping("/{id}")
    public ProposalDto get(@PathVariable Long id) {
        return ProposalDto.of(proposals.get(id));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ProposalDto accept(@PathVariable Long id) {
        return ProposalDto.of(proposals.accept(id));
    }

    @PostMapping("/{id}/accept-edited")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ProposalDto acceptEdited(@PathVariable Long id, @Valid @RequestBody EditRequest request) {
        return ProposalDto.of(proposals.acceptEdited(id, request.output()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ProposalDto reject(@PathVariable Long id) {
        return ProposalDto.of(proposals.reject(id));
    }

    public record EditRequest(@NotBlank @Size(max = 100_000) String output) {
    }

    public record ProposalPage(List<ProposalDto> items, int page, int size, long total) {
    }

    /** Siempre se presenta como propuesta, con su origen (RF-IA-19, RS-IA-11). */
    public record ProposalDto(Long id, String assistant, String status, String output, String editedOutput,
                              String promptRef, String modelId, long inputTokens, long outputTokens,
                              BigDecimal costUsd, long latencyMs, String inferenceGeo, String reviewedBy,
                              Instant reviewedAt, Instant createdAt, boolean aiGenerated) {

        static ProposalDto of(AiProposal p) {
            return new ProposalDto(p.id(), p.assistant().name(), p.status().name(), p.output(), p.editedOutput(),
                    p.promptRef(), p.usage().modelId(), p.usage().inputTokens(), p.usage().outputTokens(),
                    p.usage().costUsd(), p.usage().latencyMs(), p.usage().inferenceGeo(), p.reviewedBy(),
                    p.reviewedAt(), p.createdAt(), true);
        }
    }
}
