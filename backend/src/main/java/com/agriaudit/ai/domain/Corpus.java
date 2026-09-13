package com.agriaudit.ai.domain;

/** Corpus de recuperación semántica (RT-A06). */
public enum Corpus {
    /** Catálogo normativo compartido entre organizaciones: puntos de control y reglamentos. */
    REGULATORY_CATALOG,
    /** Evidencia de una organización: solo recuperable por esa organización (RS-IA-06). */
    ORGANIZATION_EVIDENCE;

    public boolean isShared() {
        return this == REGULATORY_CATALOG;
    }
}
