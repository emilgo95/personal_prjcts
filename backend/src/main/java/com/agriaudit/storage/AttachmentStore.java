package com.agriaudit.storage;

import com.agriaudit.storage.AttachmentDtos.AttachmentDto;

import java.util.List;
import java.util.Optional;

/**
 * Adjuntos de evidencia (API pública de {@code storage}). El módulo no sabe a qué pertenece cada
 * adjunto: el llamante comprueba antes que la entidad destino ({@link AttachmentEntityType} + id) es
 * de la organización del usuario.
 */
public interface AttachmentStore {

    /**
     * Valida (tamaño, tipo de fichero permitido) y guarda un adjunto nuevo.
     *
     * @throws com.agriaudit.common.BadRequestException si el fichero está vacío, es demasiado grande o
     *                                                  no es una imagen o un PDF
     */
    AttachmentDto store(NewAttachment attachment);

    /** Borra metadatos y contenido de todos los adjuntos de una entidad (al eliminar la entidad). */
    void deleteByEntity(AttachmentEntityType type, Long entityId);

    List<AttachmentDto> byEntity(AttachmentEntityType type, Long entityId);

    Optional<AttachmentDto> find(Long organizationId, Long attachmentId);

    /** Contenido del adjunto de la organización; {@code NotFoundException} si no existe o es ajeno. */
    byte[] content(Long organizationId, Long attachmentId);

    record NewAttachment(Long organizationId, String uploadedBy, AttachmentEntityType entityType, Long entityId,
                         String fileName, String declaredContentType, byte[] content) {
    }
}
