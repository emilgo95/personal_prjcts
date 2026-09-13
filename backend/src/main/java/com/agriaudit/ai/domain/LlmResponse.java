package com.agriaudit.ai.domain;

/**
 * Respuesta de un modelo de lenguaje.
 *
 * @param text    texto (o JSON, si se pidió salida estructurada) de la respuesta
 * @param refused el proveedor declinó responder (p. ej. {@code stop_reason: refusal})
 */
public record LlmResponse(String text, boolean refused, String modelId, long inputTokens, long outputTokens,
                          long latencyMs, String inferenceGeo) {

    public LlmResponse {
        text = text == null ? "" : text;
    }
}
