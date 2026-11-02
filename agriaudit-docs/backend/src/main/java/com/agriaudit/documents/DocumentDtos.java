package com.agriaudit.documents;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record GenerateRequest(
            @NotBlank String documentType,
            @NotBlank String format) {
    }

    public record GeneratedDocumentDto(
            Long id,
            Long auditId,
            String auditReference,
            String documentType,
            String documentTypeLabel,
            String standardCode,
            String standardName,
            String format,
            String fileName,
            Long sizeBytes,
            String sha256,
            String generatedBy,
            Instant generatedAt) {
    }

    public record DocumentOptionDto(
            String documentType,
            String label,
            List<String> formats) {
    }
}
