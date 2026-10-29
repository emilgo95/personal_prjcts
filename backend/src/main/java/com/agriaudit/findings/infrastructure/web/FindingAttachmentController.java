package com.agriaudit.findings.infrastructure.web;

import com.agriaudit.findings.application.FindingAttachmentService;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/audits/{auditId}/findings/{findingId}/attachments")
public class FindingAttachmentController {

    private final FindingAttachmentService attachments;

    public FindingAttachmentController(FindingAttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping
    public List<AttachmentDto> listForFinding(@PathVariable Long auditId, @PathVariable Long findingId) {
        return attachments.listForFinding(auditId, findingId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AttachmentDto uploadForFinding(@PathVariable Long auditId, @PathVariable Long findingId,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "containsPersonalData", defaultValue = "false")
                                          boolean personalData) {
        return attachments.uploadForFinding(auditId, findingId, file, personalData);
    }
}
