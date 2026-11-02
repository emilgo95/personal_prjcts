package com.agriaudit.trace.infrastructure.web;

import com.agriaudit.trace.ActorProvider;
import com.agriaudit.trace.TraceDtos.IntegrityReport;
import com.agriaudit.trace.TraceDtos.SealDto;
import com.agriaudit.trace.TraceDtos.TraceEntryDto;
import com.agriaudit.trace.TraceDtos.TraceFilter;
import com.agriaudit.trace.TraceDtos.TracePage;
import com.agriaudit.trace.TraceEntity;
import com.agriaudit.trace.application.TraceIntegrityService;
import com.agriaudit.trace.application.TraceQueryService;
import com.agriaudit.trace.application.TraceSealService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Consulta de la traza. Lectura: administración y calidad (toda la organización) y auditoras (sus auditorías);
 * los lectores no tienen acceso. Verificación y sellos: administración y calidad.
 */
@RestController
@RequestMapping("/api")
public class TraceController {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final TraceQueryService query;
    private final TraceIntegrityService integrity;
    private final TraceSealService seals;
    private final ActorProvider actors;

    public TraceController(TraceQueryService query, TraceIntegrityService integrity, TraceSealService seals,
                           ActorProvider actors) {
        this.query = query;
        this.integrity = integrity;
        this.seals = seals;
        this.actors = actors;
    }

    @GetMapping("/trace")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY','AUDITOR')")
    public TracePage search(@RequestParam(required = false) Long auditId,
                            @RequestParam(required = false) String entityType,
                            @RequestParam(required = false) String action,
                            @RequestParam(required = false) String user,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "50") int size) {
        return query.search(filter(auditId, entityType, null, action, user, from, to), page, size);
    }

    @GetMapping("/audits/{auditId}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY','AUDITOR')")
    public TracePage forAudit(@PathVariable Long auditId, @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "100") int size) {
        return query.forAudit(auditId, page, size);
    }

    @GetMapping("/audits/{auditId}/findings/{findingId}/trace")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY','AUDITOR')")
    public TracePage forFinding(@PathVariable Long auditId, @PathVariable Long findingId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "100") int size) {
        query.forAudit(auditId, 0, 1); // comprueba que la auditoría es visible para quien consulta
        return query.search(new TraceFilter(auditId, TraceEntity.FINDING, findingId, null, null, null, null),
                page, size, true);
    }

    @GetMapping("/trace/export")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long auditId,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String user,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        StringBuilder csv = new StringBuilder("\uFEFFnúmero;fecha;usuario;rol;entidad;referencia;acción;resumen;campo;"
                + "valor anterior;valor nuevo;huella SHA-512\n");
        query.export(filter(auditId, entityType, null, action, user, from, to), e -> {
            if (e.changes().isEmpty()) {
                csv.append(line(e, "", "", ""));
            }
            e.changes().forEach(c -> csv.append(line(e, c.label(), c.oldValue(), c.newValue())));
        });
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"trazabilidad.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/trace/verify")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public IntegrityReport verify() {
        return integrity.verify(actors.current().organizationId());
    }

    @GetMapping("/trace/seals")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public List<SealDto> listSeals() {
        return seals.list(actors.current().organizationId());
    }

    @PostMapping("/trace/seals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> sealNow() {
        return seals.seal(actors.current().organizationId())
                .<ResponseEntity<?>>map(s -> ResponseEntity.status(201).body(s))
                .orElseGet(() -> ResponseEntity.ok(java.util.Map.of("message",
                        "No hay entradas nuevas desde el último sello")));
    }

    @GetMapping("/trace/seals/export")
    @PreAuthorize("hasAnyRole('ADMIN','QUALITY')")
    public ResponseEntity<byte[]> exportSeals() {
        StringBuilder sb = new StringBuilder("Sellos de trazabilidad de AgriAudit Docs\n"
                + "Cada línea: fecha del sello | última entrada | huella SHA-512 | firma HMAC-SHA-512\n\n");
        seals.list(actors.current().organizationId()).forEach(s -> sb.append(s.sealedAt()).append(" | ")
                .append(s.lastSeq()).append(" | ").append(s.lastHash()).append(" | ").append(s.signature())
                .append('\n'));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sellos-trazabilidad.txt\"")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static TraceFilter filter(Long auditId, String entityType, Long entityId, String action, String user,
                                      LocalDate from, LocalDate to) {
        return new TraceFilter(auditId, blankToNull(entityType), entityId, blankToNull(action), user,
                from == null ? null : from.atStartOfDay(ZONE).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String line(TraceEntryDto e, String field, String oldValue, String newValue) {
        return String.join(";", String.valueOf(e.seq()), String.valueOf(e.occurredAt()),
                csv(e.userFullName() == null ? e.username() : e.userFullName() + " (" + e.username() + ")"),
                e.userRole(), e.entityType(), csv(e.entityRef()), e.action(), csv(e.summary()), csv(field),
                csv(oldValue), csv(newValue), e.hash()) + "\n";
    }

    /** Escapa para CSV y neutraliza fórmulas de hoja de cálculo (=, +, -, @). */
    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        String v = s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v + "\"";
    }
}
