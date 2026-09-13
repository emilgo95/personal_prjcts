package com.agriaudit.ai.domain;

/**
 * Prompt versionado en el repositorio (RT-A10). Cambiar un prompt es crear una versión nueva y pasar sus
 * evals; la versión activa de cada prompt es configuración.
 */
public record PromptTemplate(String id, int version, String body) {

    public PromptTemplate {
        if (id == null || !id.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("id de prompt no válido: " + id);
        }
        if (version < 1) {
            throw new IllegalArgumentException("versión de prompt no válida: " + version);
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("prompt vacío: " + id);
        }
    }

    /** Referencia trazable, p. ej. {@code finding-draft.v2}. */
    public String ref() {
        return id + ".v" + version;
    }
}
