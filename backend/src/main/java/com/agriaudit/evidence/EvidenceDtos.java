package com.agriaudit.evidence;

import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class EvidenceDtos {

    private EvidenceDtos() {
    }

    public record TypeDto(String code, String name, String description, String personalDataNotice) {
    }

    /** Alta o cambio de un registro. {@code outcome}: FAVORABLE, UNFAVORABLE o UNRATED (por defecto). */
    public record RecordRequest(
            @NotNull Long farmId,
            @NotBlank @Size(max = 40) String typeCode,
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDate issuedOn,
            LocalDate validUntil,
            String outcome,
            @Size(max = 4000) String notes,
            Boolean containsPersonalData) {
    }

    /** {@code status}: CURRENT, DUE_SOON o EXPIRED, según su fecha de vigencia. */
    public record RecordDto(
            Long id,
            Long farmId,
            String farmName,
            String typeCode,
            String typeName,
            String title,
            LocalDate issuedOn,
            LocalDate validUntil,
            String outcome,
            String notes,
            boolean containsPersonalData,
            String status,
            int documents,
            int usedIn,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** Dónde se usa un registro: un punto de control de una auditoría. */
    public record UseDto(Long linkId, Long auditId, String auditReference, String auditStatus, Long controlPointId,
                         String controlRef, String standardCode) {
    }

    public record RecordDetailDto(RecordDto record, List<AttachmentDto> attachments, List<UseDto> uses) {
    }

    /** Resultado de un cambio: el registro y a cuántas auditorías afecta. */
    public record RecordChangeDto(RecordDto record, int affectedAudits) {
    }

    public record MatrixEntryDto(Long id, String typeCode, String typeName, String standardCode, String standardName,
                                 String controlRef, String controlTitle, String status) {
    }

    public record MatrixDto(List<TypeDto> types, List<MatrixEntryDto> entries) {
    }

    public record MatrixEntryRequest(@NotBlank String typeCode, @NotBlank String standardCode,
                                     @NotBlank String controlRef) {
    }

    public record MatrixValidationRequest(boolean validated) {
    }

    public record LinkRequest(@NotNull Long controlPointId, @NotNull Long recordId) {
    }

    // ---- cobertura de una auditoría

    /** Un registro candidato (o ya vinculado, si {@code linkId} no es nulo) para un punto de control. */
    public record CoverageRecordDto(Long id, String title, LocalDate issuedOn, LocalDate validUntil, String status,
                                    String outcome, int documents, Long linkId, boolean linkable, String notLinkableReason) {
    }

    public record TypeCoverageDto(String typeCode, String typeName, String matrixStatus, String health,
                                  List<CoverageRecordDto> records) {
    }

    public record CoverageItemDto(Long controlPointId, String controlRef, String controlTitle,
                                  List<TypeCoverageDto> types, boolean covered, boolean alert) {
    }

    public record CoverageDto(Long auditId, Long farmId, boolean locked, List<CoverageItemDto> items) {
    }

    // ---- salud del sistema de gestión

    /** {@code status}: CURRENT, DUE_SOON, EXPIRED, UNFAVORABLE o MISSING. */
    public record HealthCellDto(String status, Long recordId, LocalDate validUntil) {
    }

    public record FarmHealthDto(Long farmId, String farmName, java.util.Map<String, HealthCellDto> cells) {
    }

    public record HealthDto(List<TypeDto> types, List<FarmHealthDto> farms, java.util.Map<String, Integer> summary,
                            int renewalWindowDays) {
    }
}
