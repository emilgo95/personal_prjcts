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
        String original = sanitizeFileName(cmd.fileName());
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
    public void deleteByEntity(AttachmentEntityType type, Long entityId) {
        if (entityId == null) {
            return;
        }
        for (Attachment a : attachments.findByEntityTypeAndEntityIdOrderByIdAsc(type.name(), entityId)) {
            blobs.delete(a.getStorageKey());
            attachments.delete(a);
        }
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
        deletionGuards.orderedStream().forEach(g -> g.afterDelete(type, a.getEntityId(), a.getFileName()));
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
     * Determina el tipo real del fichero por sus «magic bytes»: el tipo declarado por el cliente y la extensión
     * no se consideran fiables (un texto con extensión {@code .png} no es una imagen). Devuelve {@code null}
     * si el contenido no es una imagen ni un PDF permitidos.
     */
    private static String resolveContentType(String declared, String fileName, byte[] bytes) {
        String detected = fromMagic(bytes);
        return detected != null && ALLOWED_TYPES.contains(detected) ? detected : null;
    }

    /** Nombre de fichero sin ruta ni caracteres de control, de longitud acotada y con un valor por defecto. */
    static String sanitizeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "evidencia";
        }
        if (name.length() > 150) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, 150 - ext.length()) + ext;
        }
        return name;
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
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p'
                && isHeifBrand(new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII))) {
            return "image/heic";
        }
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        return null;
    }

    private static boolean isHeifBrand(String brand) {
        return switch (brand) {
            case "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1" -> true;
            default -> false;
        };
    }
}
