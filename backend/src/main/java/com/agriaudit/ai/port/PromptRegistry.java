package com.agriaudit.ai.port;

import com.agriaudit.ai.domain.PromptTemplate;

/** Prompts versionados (RT-A10): devuelve la versión activa de un prompt. */
public interface PromptRegistry {

    /** @throws IllegalStateException si el prompt no existe o su versión activa no está en el repositorio */
    PromptTemplate active(String promptId);
}
