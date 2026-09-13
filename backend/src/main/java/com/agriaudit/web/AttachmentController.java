package com.agriaudit.web;

import com.agriaudit.service.AuditAttachmentService;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AttachmentController {

    private final AuditAttachmentService attachments;

    public AttachmentController(AuditAttachmentService attachments) {
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
}
