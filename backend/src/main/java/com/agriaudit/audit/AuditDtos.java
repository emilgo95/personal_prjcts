package com.agriaudit.audit;

import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditRequest(
            @NotNull Long farmId,
            @NotBlank String standardCode,
            @NotBlank String auditType,
            @Size(max = 5000) String scopeDescription,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualDate,
            @Size(max = 200) String leadAuditor,
            @Size(max = 400) String auditTeam,
            @Size(max = 300) String siteAddress,
            @Size(max = 5000) String productsInScope) {
    }

    /** Página de auditorías (más recientes primero). */
    public record AuditPage(List<AuditSummaryDto> content, int page, int size, long totalElements, int totalPages) {
    }

    public record AuditSummaryDto(
            Long id,
            String reference,
            Long farmId,
            String farmName,
            String standardCode,
            String standardName,
            String auditType,
            String status,
            LocalDate plannedStartDate,
            LocalDate actualDate,
            String leadAuditor,
            Instant createdAt,
            long findingsCount,
            int assessedCount,
            int totalControlPoints,
            int progressPct) {
    }

    public record AuditDetailDto(
            Long id,
            String reference,
            String standardCode,
            String standardName,
            String standardVersion,
            String auditType,
            String status,
            String scopeDescription,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualDate,
            String leadAuditor,
            String auditTeam,
            String siteAddress,
            String productsInScope,
            Instant createdAt,
            Instant updatedAt,
            FarmDto farm,
            int totalControlPoints,
            int assessedCount,
            int progressPct,
            long findingsCount,
            List<String> allowedTransitions) {
    }

    public record AuditItemResultDto(
            Long resultId,
            Long controlPointId,
            String ref,
            String title,
            String description,
            String guidance,
            String level,
            String applicableScopes,
            String chapterCode,
            String chapterTitle,
            String compliance,
            String evidenceNotes,
            String auditorComment,
            Instant assessedAt,
            String assessedBy,
            List<AttachmentDto> attachments) {
    }

    public record ResultUpdate(
            @NotNull Long controlPointId,
            @NotBlank String compliance,
            @Size(max = 10000) String evidenceNotes,
            @Size(max = 10000) String auditorComment) {
    }

    public record ResultsUpdateRequest(@NotNull List<@Valid ResultUpdate> results) {
    }

    public record TransitionRequest(@NotBlank String status) {
    }
}
