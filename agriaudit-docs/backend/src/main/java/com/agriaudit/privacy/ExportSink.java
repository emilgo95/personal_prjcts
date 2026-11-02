package com.agriaudit.privacy;

/** Destino de lo que vuelca cada contribuyente: documentos JSON y ficheros dentro del ZIP de exportación. */
public interface ExportSink {

    /** Añade un documento JSON (p. ej. {@code datos/explotaciones.json}) con el valor indicado. */
    void json(String path, Object value);

    /** Añade un fichero binario tal cual (adjuntos y documentos generados). */
    void file(String path, byte[] content);
}
