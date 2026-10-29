package com.agriaudit.storage;

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
            boolean image,
            /**
             * Marca «contiene datos personales». Solo la ven administración, calidad y quien subió el adjunto;
             * para el resto es {@code null} (no se informa, ni en un sentido ni en otro).
             */
            Boolean containsPersonalData) {
    }
}
