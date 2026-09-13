package com.agriaudit.ai.domain;

import java.util.Objects;

/**
 * Fragmento de texto con su vector, listo para indexar.
 *
 * @param sourceType tipo de origen (p. ej. {@code CONTROL_POINT}, {@code ATTACHMENT})
 * @param model      modelo de embeddings que produjo el vector; solo se comparan vectores del mismo modelo
 */
public record EmbeddedChunk(CorpusScope scope, String sourceType, String sourceId, int chunkIndex, String content,
                            String model, float[] vector) {

    public EmbeddedChunk {
        Objects.requireNonNull(scope, "scope");
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("Origen del fragmento requerido");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex no puede ser negativo");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Fragmento vacío");
        }
        if (model == null || model.isBlank() || vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector y modelo requeridos");
        }
    }
}
