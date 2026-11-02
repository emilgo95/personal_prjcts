package com.agriaudit.evidence.infrastructure.web;

import com.agriaudit.evidence.EvidenceDtos.HealthDto;
import com.agriaudit.evidence.EvidenceDtos.MatrixDto;
import com.agriaudit.evidence.EvidenceDtos.MatrixEntryDto;
import com.agriaudit.evidence.EvidenceDtos.MatrixEntryRequest;
import com.agriaudit.evidence.EvidenceDtos.MatrixValidationRequest;
import com.agriaudit.evidence.EvidenceDtos.RecordChangeDto;
import com.agriaudit.evidence.EvidenceDtos.RecordDetailDto;
import com.agriaudit.evidence.EvidenceDtos.RecordDto;
import com.agriaudit.evidence.EvidenceDtos.RecordRequest;
import com.agriaudit.evidence.EvidenceDtos.TypeDto;
import com.agriaudit.evidence.application.EvidenceHealthService;
import com.agriaudit.evidence.application.EvidenceMatrixService;
import com.agriaudit.evidence.application.EvidenceRecordService;
import com.agriaudit.storage.AttachmentDtos.AttachmentDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Registros de evidencia reutilizables, matriz de equivalencias y panel de salud del sistema de gestión. */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceRecordService recordService;
    private final EvidenceMatrixService matrixService;
    private final EvidenceHealthService healthService;

    public EvidenceController(EvidenceRecordService recordService, EvidenceMatrixService matrixService,
                              EvidenceHealthService healthService) {
        this.recordService = recordService;
        this.matrixService = matrixService;
        this.healthService = healthService;
    }

    @GetMapping("/types")
    public List<TypeDto> types() {
        return recordService.types();
    }

    @GetMapping("/records")
    public List<RecordDto> records(@RequestParam(required = false) Long farmId, @RequestParam(required = false) String typeCode) {
        return recordService.list(farmId, typeCode);
    }

    @GetMapping("/records/{id}")
    public RecordDetailDto record(@PathVariable Long id) {
        return recordService.get(id);
    }

    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','QUALITY')")
    public RecordChangeDto create(@Valid @RequestBody RecordRequest request) {
        return recordService.create(request);
    }

    @PutMapping("/records/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','QUALITY')")
    public RecordChangeDto update(@PathVariable Long id, @Valid @RequestBody RecordRequest request) {
        return recordService.update(id, request);
    }

    @DeleteMapping("/records/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public void delete(@PathVariable Long id) {
        recordService.delete(id);
    }

    @GetMapping("/records/{id}/attachments")
    public List<AttachmentDto> documents(@PathVariable Long id) {
        return recordService.documents(id);
    }

    @PostMapping(value = "/records/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','QUALITY')")
    public AttachmentDto upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return recordService.uploadDocument(id, file);
    }

    @GetMapping("/health")
    public HealthDto health() {
        return healthService.health();
    }

    @GetMapping("/matrix")
    public MatrixDto matrix() {
        return matrixService.get();
    }

    @PostMapping("/matrix")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public MatrixEntryDto addEquivalence(@Valid @RequestBody MatrixEntryRequest request) {
        return matrixService.add(request);
    }

    @PutMapping("/matrix/{id}/validation")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public MatrixEntryDto validate(@PathVariable Long id, @RequestBody MatrixValidationRequest request) {
        return matrixService.validate(id, request.validated());
    }

    @DeleteMapping("/matrix/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public void removeEquivalence(@PathVariable Long id) {
        matrixService.remove(id);
    }
}
