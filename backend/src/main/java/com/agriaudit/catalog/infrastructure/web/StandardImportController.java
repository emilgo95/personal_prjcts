package com.agriaudit.catalog.infrastructure.web;

import com.agriaudit.catalog.CatalogDtos.StandardSummaryDto;
import com.agriaudit.catalog.StandardImportDtos.ImportMetadata;
import com.agriaudit.catalog.StandardImportDtos.ImportPreview;
import com.agriaudit.catalog.application.ImportTemplate;
import com.agriaudit.catalog.application.StandardImportService;
import com.agriaudit.common.BadRequestException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Importación de estándares propios de la organización y gestión de su visibilidad (administración y calidad). */
@RestController
@RequestMapping("/api/standards")
@PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
public class StandardImportController {

    private final StandardImportService importer;

    public StandardImportController(StandardImportService importer) {
        this.importer = importer;
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-listado.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ImportTemplate.build());
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview preview(@RequestParam("file") MultipartFile file, ImportMetadata meta) {
        return importer.preview(meta, file.getOriginalFilename(), bytes(file));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StandardSummaryDto> importStandard(@RequestParam("file") MultipartFile file,
                                                             ImportMetadata meta) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(importer.importStandard(meta, file.getOriginalFilename(), bytes(file)));
    }

    @PutMapping("/{code}/archived")
    public void archive(@PathVariable String code, @RequestParam boolean value) {
        importer.setArchived(code, value);
    }

    @PutMapping("/{code}/hidden")
    public void hide(@PathVariable String code, @RequestParam boolean value) {
        importer.setHidden(code, value);
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El fichero está vacío");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el fichero subido", e);
        }
    }
}
