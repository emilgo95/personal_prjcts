package com.agriaudit.audit.infrastructure.web;

import com.agriaudit.audit.application.AuditAttachmentService;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/audits/{auditId}")
public class AuditAttachmentController {

    private final AuditAttachmentService attachments;

    public AuditAttachmentController(AuditAttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/attachments")
    public List<AttachmentDto> listForAudit(@PathVariable Long auditId) {
        return attachments.listForAudit(auditId);
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AttachmentDto uploadForAudit(@PathVariable Long auditId, @RequestParam("file") MultipartFile file) {
        return attachments.uploadForAudit(auditId, file);
    }

    @GetMapping("/results/{controlPointId}/attachments")
    public List<AttachmentDto> listForControlPoint(@PathVariable Long auditId, @PathVariable Long controlPointId) {
        return attachments.listForControlPoint(auditId, controlPointId);
    }

    @PostMapping(value = "/results/{controlPointId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AttachmentDto uploadForControlPoint(@PathVariable Long auditId, @PathVariable Long controlPointId,
                                               @RequestParam("file") MultipartFile file) {
        return attachments.uploadForControlPoint(auditId, controlPointId, file);
    }
}
