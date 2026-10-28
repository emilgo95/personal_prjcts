package com.agriaudit.trace;

import java.time.Instant;
import java.util.List;

public final class TraceDtos {

    private TraceDtos() {
    }

    public record TraceEntryDto(
            Long id,
            long seq,
            Instant occurredAt,
            Instant createdAt,
            Long userId,
            String username,
            String userFullName,
            String userRole,
            String entityType,
            Long entityId,
            String entityRef,
            String parentType,
            Long parentId,
            Long auditId,
            String action,
            String summary,
            List<FieldChange> changes,
            String prevHash,
            String hash,
            String signature) {
    }

    public record TracePage(List<TraceEntryDto> content, int page, int size, long totalElements, int totalPages) {
    }

    /** Resultado de comprobar la cadena: {@code ok} y, si no, la primera entrada que no cuadra. */
    public record IntegrityReport(
            boolean ok,
            long entriesChecked,
            int sealsChecked,
            Long firstBrokenSeq,
            String message,
            Instant verifiedAt) {
    }

    public record SealDto(Long id, Instant sealedAt, long lastSeq, String lastHash, String signature) {
    }

    /** Filtros de consulta de la traza. */
    public record TraceFilter(Long auditId, String entityType, Long entityId, String action, String username,
                              Instant from, Instant to) {
    }
}
