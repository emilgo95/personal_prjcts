package com.agriaudit.privacy.infrastructure.web;

import com.agriaudit.privacy.application.OrganizationExportService;
import com.agriaudit.trace.ActorProvider;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Descarga de todos los datos de la organización en un ZIP (solo administración). Queda constancia en la traza. */
@RestController
@RequestMapping("/api/organization")
public class OrganizationExportController {

    private final OrganizationExportService exporter;
    private final ChangeRecorder trace;
    private final ActorProvider actors;

    public OrganizationExportController(OrganizationExportService exporter, ChangeRecorder trace,
                                        ActorProvider actors) {
        this.exporter = exporter;
        this.trace = trace;
        this.actors = actors;
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<byte[]> export() throws IOException {
        Long orgId = actors.current().organizationId();
        trace.record(ChangeEntry.of(TraceEntity.ORGANIZATION, orgId, "organización " + orgId, null,
                TraceAction.GENERATE, "Exportó todos los datos de la organización", List.of()));
        // En memoria y no en streaming: así el ZIP se genera con la sesión y la transacción de la petición.
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        exporter.export(orgId, zip);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"datos-organizacion-" + LocalDate.now() + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip.toByteArray());
    }
}
