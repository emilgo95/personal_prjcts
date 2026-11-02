package com.agriaudit.ai.domain;

/**
 * Escalón de modelo (RT-A07). Qué modelo concreto y qué región de inferencia corresponde a cada escalón
 * es configuración, no código: el dominio solo expresa la necesidad.
 */
public enum ModelTier {
    /** Extracción y clasificación de alto volumen. */
    FAST,
    /** Redacción, RAG y razonamiento medio. */
    BALANCED
}
