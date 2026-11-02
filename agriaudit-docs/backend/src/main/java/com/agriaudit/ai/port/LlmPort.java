package com.agriaudit.ai.port;

import com.agriaudit.ai.domain.LlmRequest;
import com.agriaudit.ai.domain.LlmResponse;

/**
 * Puerto de modelo de lenguaje (RT-A01). Qué proveedor hay detrás (Claude API, Claude en Bedrock/Vertex UE,
 * un modelo autoalojado o un doble de pruebas) es configuración ({@code agriaudit.ai.provider}).
 */
public interface LlmPort {

    /** Si hay un proveedor configurado y utilizable. Si no, las funciones de IA se ocultan (RS-IA-10). */
    boolean isAvailable();

    /**
     * @throws com.agriaudit.common.ServiceUnavailableException si el proveedor no responde, limita la tasa o
     *                                                          rechaza la petición
     */
    LlmResponse complete(LlmRequest request);
}
