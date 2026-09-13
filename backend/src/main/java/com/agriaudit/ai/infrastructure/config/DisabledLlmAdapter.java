package com.agriaudit.ai.infrastructure.config;

import com.agriaudit.ai.domain.LlmRequest;
import com.agriaudit.ai.domain.LlmResponse;
import com.agriaudit.ai.port.LlmPort;
import com.agriaudit.common.ServiceUnavailableException;

/** IA desactivada ({@code provider=none}): valor por defecto; el frontal oculta las funciones de IA. */
enum DisabledLlmAdapter implements LlmPort {
    INSTANCE;

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        throw new ServiceUnavailableException("Los asistentes de IA no están configurados en este entorno");
    }
}
