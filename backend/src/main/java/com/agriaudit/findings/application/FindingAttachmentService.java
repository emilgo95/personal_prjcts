package com.agriaudit.findings.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.storage.AttachmentStore.NewAttachment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Adjuntos de evidencia de una no conformidad: comprueba que la NC pertenece a una auditoría de la
 * organización del usuario y delega el almacenamiento en {@link AttachmentStore}.
 */
@Service
@Transactional
public class FindingAttachmentService {

    private final AttachmentStore attachments;
    private final FindingService findingService;

    public FindingAttachmentService(AttachmentStore attachments, FindingService findingService) {
        this.attachments = attachments;
        this.findingService = findingService;
    }

    public AttachmentDto uploadForFinding(Long auditId, Long findingId, MultipartFile file) {
        Long id = findingService.requireOwnedFinding(auditId, findingId).getId();
        if (file == null) {
            throw new BadRequestException("El fichero está vacío");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fichero subido", e);
        }
        return attachments.store(new NewAttachment(SecurityUtils.currentOrganizationId(),
                SecurityUtils.currentUsername(), AttachmentEntityType.FINDING, id, file.getOriginalFilename(),
                file.getContentType(), bytes));
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> listForFinding(Long auditId, Long findingId) {
        findingService.requireOwnedFinding(auditId, findingId);
        return attachments.byEntity(AttachmentEntityType.FINDING, findingId);
    }
}
