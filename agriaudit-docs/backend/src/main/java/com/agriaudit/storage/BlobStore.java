package com.agriaudit.storage;

/**
 * Almacén de contenido binario (adjuntos de evidencia y documentos generados) tras un puerto (RT-32).
 * Hoy: sistema de ficheros local; objetivo: adaptador S3-compatible con prefijo por organización.
 */
public interface BlobStore {

    /** Guarda el contenido bajo la categoría indicada y devuelve la clave de almacenamiento. */
    String store(byte[] data, String fileName, String category);

    /** Contenido de la clave; {@code NotFoundException} si no existe. */
    byte[] load(String storageKey);

    /** Borra el contenido si existe; no falla si ya no está. */
    void delete(String storageKey);
}
