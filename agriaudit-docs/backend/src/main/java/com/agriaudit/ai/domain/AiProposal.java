package com.agriaudit.ai.domain;

import com.agriaudit.common.ConflictException;

import java.time.Instant;
import java.util.Objects;

/**
 * Propuesta de un asistente de IA (RT-A02, RS-IA-01). Toda salida de IA es una propuesta {@code PENDING}
 * que no escribe en ninguna tabla de dominio hasta que una persona la acepta (tal cual o editada); también
 * puede rechazarla. Cada propuesta conserva qué modelo, qué versión de prompt y qué entradas la produjeron,
 * su coste y quién la revisó y cuándo (RS-IA-07).
 */
public final class AiProposal {

    private final Long id;
    private final long organizationId;
    private final AiAssistant assistant;
    private final String inputRefs;
    private final String promptRef;
    private final String output;
    private final AiUsage usage;
    private final Instant createdAt;
    private AiProposalStatus status;
    private String reviewedBy;
    private Instant reviewedAt;
    private String editedOutput;

    private AiProposal(Long id, long organizationId, AiAssistant assistant, String inputRefs, String promptRef,
                       String output, AiUsage usage, Instant createdAt, AiProposalStatus status,
                       String reviewedBy, Instant reviewedAt, String editedOutput) {
        this.id = id;
        this.organizationId = organizationId;
        this.assistant = Objects.requireNonNull(assistant, "assistant");
        this.inputRefs = inputRefs;
        this.promptRef = Objects.requireNonNull(promptRef, "promptRef");
        this.output = output == null ? "" : output;
        this.usage = Objects.requireNonNull(usage, "usage");
        this.createdAt = createdAt;
        this.status = Objects.requireNonNull(status, "status");
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.editedOutput = editedOutput;
    }

    /** Propuesta nueva, pendiente de revisión. */
    public static AiProposal pending(long organizationId, AiAssistant assistant, String inputRefs, String promptRef,
                                     String output, AiUsage usage, Instant createdAt) {
        return new AiProposal(null, organizationId, assistant, inputRefs, promptRef, output, usage, createdAt,
                AiProposalStatus.PENDING, null, null, null);
    }

    /** Reconstrucción desde el almacenamiento. */
    public static AiProposal restore(Long id, long organizationId, AiAssistant assistant, String inputRefs,
                                     String promptRef, String output, AiUsage usage, Instant createdAt,
                                     AiProposalStatus status, String reviewedBy, Instant reviewedAt,
                                     String editedOutput) {
        return new AiProposal(Objects.requireNonNull(id, "id"), organizationId, assistant, inputRefs, promptRef,
                output, usage, createdAt, status, reviewedBy, reviewedAt, editedOutput);
    }

    public void accept(String reviewer, Instant when) {
        review(AiProposalStatus.ACCEPTED, reviewer, when);
    }

    /** La persona acepta la propuesta con cambios; se conserva la salida original y la editada. */
    public void acceptEdited(String reviewer, String edited, Instant when) {
        if (edited == null || edited.isBlank()) {
            throw new IllegalArgumentException("La versión editada no puede estar vacía");
        }
        review(AiProposalStatus.EDITED, reviewer, when);
        this.editedOutput = edited;
    }

    public void reject(String reviewer, Instant when) {
        review(AiProposalStatus.REJECTED, reviewer, when);
    }

    private void review(AiProposalStatus target, String reviewer, Instant when) {
        if (status != AiProposalStatus.PENDING) {
            throw new ConflictException("La propuesta ya fue revisada (" + status + ")");
        }
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("La revisión exige un revisor identificado");
        }
        this.status = target;
        this.reviewedBy = reviewer;
        this.reviewedAt = Objects.requireNonNull(when, "when");
    }

    /** Salida que debe aplicar el caso de uso tras la revisión: la editada si la hay, si no la original. */
    public String effectiveOutput() {
        return editedOutput != null ? editedOutput : output;
    }

    public boolean isApplicable() {
        return status == AiProposalStatus.ACCEPTED || status == AiProposalStatus.EDITED;
    }

    public Long id() { return id; }
    public long organizationId() { return organizationId; }
    public AiAssistant assistant() { return assistant; }
    public String inputRefs() { return inputRefs; }
    public String promptRef() { return promptRef; }
    public String output() { return output; }
    public AiUsage usage() { return usage; }
    public Instant createdAt() { return createdAt; }
    public AiProposalStatus status() { return status; }
    public String reviewedBy() { return reviewedBy; }
    public Instant reviewedAt() { return reviewedAt; }
    public String editedOutput() { return editedOutput; }
}
