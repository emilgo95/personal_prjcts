package com.agriaudit.web;

import com.agriaudit.service.AttachmentService;
import com.agriaudit.service.AttachmentService.DownloadedFile;
import com.agriaudit.web.dto.AttachmentDtos.AttachmentDto;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/audits/{auditId}/attachments")
    public List<AttachmentDto> listForAudit(@PathVariable Long auditId) {
        return attachments.listForAudit(auditId);
    }

    @PostMapping(value = "/audits/{auditId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AttachmentDto uploadForAudit(@PathVariable Long auditId, @RequestParam("file") MultipartFile file) {
        return attachments.uploadForAudit(auditId, file);
    }

    @GetMapping("/audits/{auditId}/results/{controlPointId}/attachments")
    public List<AttachmentDto> listForControlPoint(@PathVariable Long auditId, @PathVariable Long controlPointId) {
        return attachments.listForControlPoint(auditId, controlPointId);
    }

    @PostMapping(value = "/audits/{auditId}/results/{controlPointId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AttachmentDto uploadForControlPoint(@PathVariable Long auditId, @PathVariable Long controlPointId,
                                               @RequestParam("file") MultipartFile file) {
        return attachments.uploadForControlPoint(auditId, controlPointId, file);
    }

    @GetMapping("/audits/{auditId}/findings/{findingId}/attachments")
    public List<AttachmentDto> listForFinding(@PathVariable Long auditId, @PathVariable Long findingId) {
        return attachments.listForFinding(auditId, findingId);
    }

    @PostMapping(value = "/audits/{auditId}/findings/{findingId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AttachmentDto uploadForFinding(@PathVariable Long auditId, @PathVariable Long findingId,
                                          @RequestParam("file") MultipartFile file) {
        return attachments.uploadForFinding(auditId, findingId, file);
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DownloadedFile f = attachments.download(id);
        String encoded = new String(f.fileName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + encoded + "\"")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(new ByteArrayResource(f.data()));
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public void delete(@PathVariable Long id) {
        attachments.delete(id);
    }
}
