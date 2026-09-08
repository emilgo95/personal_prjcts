package com.agriaudit.service;

import com.agriaudit.domain.Attachment;
import com.agriaudit.domain.enums.AttachmentEntityType;
import com.agriaudit.repo.AttachmentRepository;
import com.agriaudit.web.dto.AttachmentDtos.AttachmentDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consulta de adjuntos sin dependencias de servicios de negocio, para evitar
 * ciclos entre {@code AttachmentService}, {@code AuditService} y {@code FindingService}.
 */
@Component
public class AttachmentLookup {

    private final AttachmentRepository attachments;

    public AttachmentLookup(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> byEntity(AttachmentEntityType type, Long entityId) {
        if (entityId == null) {
            return List.of();
        }
        return attachments.findByEntityTypeAndEntityIdOrderByIdAsc(type.name(), entityId)
                .stream().map(AttachmentLookup::toDto).toList();
    }

    public static AttachmentDto toDto(Attachment a) {
        boolean image = a.getContentType() != null && a.getContentType().startsWith("image/");
        return new AttachmentDto(a.getId(), a.getEntityType(), a.getEntityId(), a.getFileName(),
                a.getContentType(), a.getSizeBytes(), a.getUploadedBy(), a.getUploadedAt(), image);
    }
}
