package com.agriaudit.findings;

import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FindingDtos {

    private FindingDtos() {
    }

    public record CorrectiveActionRequest(
            @Size(max = 10000) String rootCause,
            @Size(max = 10000) String correction,
            @Size(max = 10000) String preventiveAction,
            @Size(max = 200) String responsiblePerson,
            LocalDate targetDate,
            LocalDate completedDate,
            @Size(max = 10000) String verificationNotes,
            @Size(max = 200) String verifiedBy,
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
            @Size(max = 400) String title,
            @Size(max = 10000) String description,
            @Size(max = 150) String area,
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
            List<AttachmentDto> attachments,
            Instant closedAt,
            /** Motivo del retraso si la auditoría se envió con esta NC vencida (null si no hizo falta). */
            String lateJustification) {

        public FindingDto(Long id, Long auditId, Long controlPointId, String controlPointRef, String controlPointTitle,
                          String reference, String classification, String title, String description, String area,
                          LocalDate dueDate, String status, Instant raisedAt,
                          List<CorrectiveActionDto> correctiveActions, List<AttachmentDto> attachments) {
            this(id, auditId, controlPointId, controlPointRef, controlPointTitle, reference, classification, title,
                    description, area, dueDate, status, raisedAt, correctiveActions, attachments, null, null);
        }
    }
}
