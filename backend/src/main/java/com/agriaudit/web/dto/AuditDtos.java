package com.agriaudit.web.dto;

import com.agriaudit.web.dto.AttachmentDtos.AttachmentDto;
import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
            String scopeDescription,
            LocalDate plannedStartDate,
            LocalDate plannedEndDate,
            LocalDate actualDate,
            String leadAuditor,
            String auditTeam,
            String siteAddress,
            String productsInScope) {
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
            long findingsCount) {
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
            String evidenceNotes,
            String auditorComment) {
    }

    public record ResultsUpdateRequest(@NotNull List<ResultUpdate> results) {
    }

    public record TransitionRequest(@NotBlank String status) {
    }
}
