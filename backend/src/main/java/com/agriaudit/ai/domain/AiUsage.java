package com.agriaudit.ai.domain;

import java.math.BigDecimal;

/**
 * Traza de coste y rendimiento de una llamada a un modelo (RT-A02, RT-A08).
 *
 * @param costUsd      coste estimado con la tarifa configurada del modelo (USD, la moneda de la tarifa)
 * @param inferenceGeo región de inferencia informada por el proveedor, si la informa
 */
public record AiUsage(String modelId, long inputTokens, long outputTokens, long latencyMs, BigDecimal costUsd,
                      String inferenceGeo) {
}
