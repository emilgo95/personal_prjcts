package com.agriaudit.documents.infrastructure.web;

import com.agriaudit.documents.application.DocumentGenerationService;
import com.agriaudit.documents.application.DocumentGenerationService.DownloadedDocument;
import com.agriaudit.documents.DocumentDtos.DocumentOptionDto;
import com.agriaudit.documents.DocumentDtos.GenerateRequest;
import com.agriaudit.documents.DocumentDtos.GeneratedDocumentDto;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentGenerationService documents;

    public DocumentController(DocumentGenerationService documents) {
        this.documents = documents;
    }

    @GetMapping("/audits/{auditId}/document-options")
    public List<DocumentOptionDto> options(@PathVariable Long auditId) {
        return documents.optionsForAudit(auditId);
    }

    @GetMapping("/audits/{auditId}/documents")
    public List<GeneratedDocumentDto> listForAudit(@PathVariable Long auditId) {
        return documents.listForAudit(auditId);
    }

    @PostMapping("/audits/{auditId}/documents/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','QUALITY')")
    public GeneratedDocumentDto generate(@PathVariable Long auditId, @Valid @RequestBody GenerateRequest request) {
        return documents.generate(auditId, request.documentType(), request.format());
    }

    @GetMapping("/documents")
    public List<GeneratedDocumentDto> listForOrganization() {
        return documents.listForOrganization();
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long documentId) {
        DownloadedDocument doc = documents.download(documentId);
        String encoded = new String(doc.fileName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encoded + "\"")
                .contentType(MediaType.parseMediaType(doc.contentType()))
                .body(new ByteArrayResource(doc.data()));
    }
}
