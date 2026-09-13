package com.agriaudit.findings;

import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FindingDtos {

    private FindingDtos() {
    }

    public record CorrectiveActionRequest(
            String rootCause,
            String correction,
            String preventiveAction,
            String responsiblePerson,
            LocalDate targetDate,
            LocalDate completedDate,
            String verificationNotes,
            String verifiedBy,
            String status) {
    }

    public record CorrectiveActionDto(
            Long id,
            Long findingId,
            String rootCause,
            String correction,
            String preventiveAction,
            String responsiblePerson,
            LocalDate targetDate,
            LocalDate completedDate,
            String verificationNotes,
            String verifiedBy,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record FindingRequest(
            Long controlPointId,
            @NotBlank String classification,
            String title,
            String description,
            String area,
            LocalDate dueDate,
            String status) {
    }

    public record FindingDto(
            Long id,
            Long auditId,
            Long controlPointId,
            String controlPointRef,
            String controlPointTitle,
            String reference,
            String classification,
            String title,
            String description,
            String area,
            LocalDate dueDate,
            String status,
            Instant raisedAt,
            List<CorrectiveActionDto> correctiveActions,
            List<AttachmentDto> attachments) {
    }
}
