package com.agriaudit.ai.domain;

/**
 * Fragmento recuperado por similitud, con su origen para poder citarlo (RT-A05).
 *
 * @param score similitud coseno en [-1, 1]; mayor es más parecido
 */
public record RetrievedChunk(String sourceType, String sourceId, int chunkIndex, String content, double score) {
}
