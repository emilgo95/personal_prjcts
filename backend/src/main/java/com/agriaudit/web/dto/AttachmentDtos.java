package com.agriaudit.web.dto;

import java.time.Instant;

public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record AttachmentDto(
            Long id,
            String entityType,
            Long entityId,
            String fileName,
            String contentType,
            Long sizeBytes,
            String uploadedBy,
            Instant uploadedAt,
            boolean image) {
    }
}
