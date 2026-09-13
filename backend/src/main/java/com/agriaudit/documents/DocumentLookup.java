package com.agriaudit.documents;

/** Documentos generados vistos desde otros módulos (API pública de {@code documents}). */
public interface DocumentLookup {

    long countGenerated(Long organizationId);
}
