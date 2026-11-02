package com.agriaudit.ai.domain;

/** Asistentes de IA del catálogo (ver IA-en-AgriAudit-Docs §2). Los de la Fase 1 (MVP) primero. */
public enum AiAssistant {
    /** A1 · Extractor de labores de campo (O1). */
    FIELD_LOG_EXTRACTION,
    /** A2 · Clasificador y extractor de documentos de evidencia (O4). */
    EVIDENCE_CLASSIFICATION,
    /** B1 · Copiloto de preparación de auditoría (O4). */
    AUDIT_PREPARATION,
    /** C1 · Redactor de no conformidades y CAPA (O3). */
    FINDING_DRAFT,
    /** F1 · Copiloto normativo con cita (transversal). */
    REGULATORY_QA,
    /** Uso interno: comprobación de conectividad y tests del patrón propuesta. */
    DIAGNOSTIC
}
