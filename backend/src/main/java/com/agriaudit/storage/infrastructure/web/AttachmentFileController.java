package com.agriaudit.storage.infrastructure.web;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import com.agriaudit.storage.application.AttachmentStoreService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/** Operaciones sobre un adjunto por id, independientes de la entidad a la que pertenece. */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentFileController {

    private final AttachmentStoreService attachments;

    public AttachmentFileController(AttachmentStoreService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Long orgId = SecurityUtils.currentOrganizationId();
        AttachmentDto a = attachments.find(orgId, id)
                .orElseThrow(() -> new NotFoundException("Adjunto no encontrado: " + id));
        byte[] data = attachments.content(orgId, id);
        String contentType = a.contentType() == null ? "application/octet-stream" : a.contentType();
        String encoded = new String(a.fileName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encoded + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new ByteArrayResource(data));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public void delete(@PathVariable Long id) {
        attachments.delete(SecurityUtils.currentOrganizationId(), id);
    }
}
