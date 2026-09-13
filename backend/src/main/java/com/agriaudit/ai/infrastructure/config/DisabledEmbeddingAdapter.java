package com.agriaudit.ai.infrastructure.config;

import com.agriaudit.ai.port.EmbeddingPort;
import com.agriaudit.common.ServiceUnavailableException;

import java.util.List;

/** Embeddings desactivados ({@code agriaudit.ai.embeddings.provider=none}), valor por defecto. */
enum DisabledEmbeddingAdapter implements EmbeddingPort {
    INSTANCE;

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String model() {
        return "none";
    }

    @Override
    public int dimensions() {
        return 0;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        throw new ServiceUnavailableException("La búsqueda semántica no está configurada en este entorno");
    }
}
