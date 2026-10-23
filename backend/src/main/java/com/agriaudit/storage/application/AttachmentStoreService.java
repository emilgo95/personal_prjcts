package com.agriaudit.storage.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.storage.AttachmentDeletionGuard;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.storage.BlobStore;
import com.agriaudit.storage.infrastructure.persistence.Attachment;
import com.agriaudit.storage.infrastructure.persistence.AttachmentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Implementación de {@link AttachmentStore}: validación de ficheros, metadatos en BD y contenido en {@link BlobStore}. */
@Service
@Transactional
public class AttachmentStoreService implements AttachmentStore {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif",
            "application/pdf");

    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private final AttachmentRepository attachments;
    private final BlobStore blobs;
    private final ObjectProvider<AttachmentDeletionGuard> deletionGuards;

    public AttachmentStoreService(AttachmentRepository attachments, BlobStore blobs,
                                  ObjectProvider<AttachmentDeletionGuard> deletionGuards) {
        this.attachments = attachments;
        this.blobs = blobs;
        // Se resuelven al borrar, no al arrancar: los guardianes dependen de módulos que a su vez usan este servicio.
        this.deletionGuards = deletionGuards;
    }

    @Override
    public AttachmentDto store(NewAttachment cmd) {
        byte[] bytes = cmd.content();
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("El fichero está vacío");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadRequestException("El fichero supera el tamaño máximo de 25 MB");
        }
        String declared = cmd.declaredContentType() == null ? "" : cmd.declaredContentType().toLowerCase();
        String original = cmd.fileName() == null ? "evidencia" : cmd.fileName();
        String resolved = resolveContentType(declared, original, bytes);
        if (resolved == null) {
            throw new BadRequestException("Tipo de fichero no permitido. Se aceptan imágenes (JPG, PNG, GIF, WEBP, HEIC) y PDF.");
        }

        String key = blobs.store(bytes, original, "attachments");
        Attachment a = new Attachment();
        a.setOrganizationId(cmd.organizationId());
        a.setEntityType(cmd.entityType().name());
        a.setEntityId(cmd.entityId());
        a.setFileName(original);
        a.setContentType(resolved);
        a.setSizeBytes((long) bytes.length);
        a.setStorageKey(key);
        a.setUploadedBy(cmd.uploadedBy());
        return toDto(attachments.save(a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentDto> byEntity(AttachmentEntityType type, Long entityId) {
        if (entityId == null) {
            return List.of();
        }
        return attachments.findByEntityTypeAndEntityIdOrderByIdAsc(type.name(), entityId)
                .stream().map(AttachmentStoreService::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttachmentDto> find(Long organizationId, Long attachmentId) {
        return attachments.findByIdAndOrganizationId(attachmentId, organizationId).map(AttachmentStoreService::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] content(Long organizationId, Long attachmentId) {
        return blobs.load(require(organizationId, attachmentId).getStorageKey());
    }

    /** Borra metadatos y contenido de un adjunto de la organización. */
    public void delete(Long organizationId, Long attachmentId) {
        Attachment a = require(organizationId, attachmentId);
        AttachmentEntityType type = AttachmentEntityType.valueOf(a.getEntityType());
        deletionGuards.orderedStream().forEach(g -> g.checkCanDelete(type, a.getEntityId()));
        blobs.delete(a.getStorageKey());
        attachments.delete(a);
    }

    private Attachment require(Long organizationId, Long attachmentId) {
        return attachments.findByIdAndOrganizationId(attachmentId, organizationId)
                .orElseThrow(() -> new NotFoundException("Adjunto no encontrado: " + attachmentId));
    }

    static AttachmentDto toDto(Attachment a) {
        boolean image = a.getContentType() != null && a.getContentType().startsWith("image/");
        return new AttachmentDto(a.getId(), a.getEntityType(), a.getEntityId(), a.getFileName(),
                a.getContentType(), a.getSizeBytes(), a.getUploadedBy(), a.getUploadedAt(), image);
    }

    /**
     * Determina un tipo de contenido válido a partir del que declara el cliente, la extensión
     * del fichero y los "magic bytes". Devuelve {@code null} si no es imagen ni PDF.
     */
    private static String resolveContentType(String declared, String fileName, byte[] bytes) {
        if (ALLOWED_TYPES.contains(declared)) {
            return declared;
        }
        String byExt = fromExtension(fileName);
        if (byExt != null) {
            return byExt;
        }
        return fromMagic(bytes);
    }

    private static String fromExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return switch (fileName.substring(dot + 1).toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "pdf" -> "application/pdf";
            default -> null;
        };
    }

    private static String fromMagic(byte[] b) {
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            return "image/heic";
        }
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        return null;
    }
}
