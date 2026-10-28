package com.agriaudit.storage;

/**
 * Lo que {@code storage} necesita saber de los módulos dueños de los adjuntos antes de borrar uno: si la
 * entidad a la que pertenece admite cambios en este momento. Lo declara {@code storage} y lo implementan
 * {@code audit} y {@code findings} (inversión de dependencias; {@code storage} no depende de ninguno).
 */
public interface AttachmentDeletionGuard {

    /**
     * Comprueba si se puede borrar un adjunto de la entidad indicada.
     *
     * @throws com.agriaudit.common.ConflictException si la entidad está congelada (p. ej. auditoría cerrada)
     */
    void checkCanDelete(AttachmentEntityType type, Long entityId);

    /** Se llama tras borrar el adjunto: el módulo dueño de la entidad puede dejar constancia (traza). */
    default void afterDelete(AttachmentEntityType type, Long entityId, String fileName) {
    }
}
