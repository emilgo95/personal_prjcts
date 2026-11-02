package com.agriaudit;

import com.agriaudit.support.FirstLogin;
import com.agriaudit.support.TestPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Traza de cambios (RF-TR-*): qué se anota, con qué detalle, quién la ve, que es inalterable y que cualquier
 * manipulación se detecta (cadena SHA-512 + firma HMAC + sellos).
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TraceIntegrationTest {

    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-trace");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.demo.seed", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void loginAsAdmin() {
        admin = login("admin@demo.test", "Admin123!");
    }

    // ---------------------------------------------------------------- utilidades

    private String login(String email, String password) {
        ResponseEntity<JsonNode> r = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), json(null)), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody().get("accessToken").asText();
    }

    private HttpHeaders json(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            h.setBearerAuth(bearer);
        }
        return h;
    }

    private ResponseEntity<JsonNode> call(String token, HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, json(token)), JsonNode.class);
    }

    private long id(ResponseEntity<JsonNode> r) {
        assertThat(r.getStatusCode().is2xxSuccessful()).as("respuesta %s: %s", r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody().get("id").asLong();
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Crea un usuario con el rol indicado y devuelve su token. */
    private String userWithRole(String role, String fullName) {
        String email = role.toLowerCase() + "." + unique() + "@demo.test";
        id(call(admin, HttpMethod.POST, "/api/users",
                Map.of("email", email, "fullName", fullName, "role", role, "password", "Abcd1234!")));
        return FirstLogin.withTemporaryPassword(rest, email, "Abcd1234!");
    }

    private long newFarm(String token) {
        return id(call(token, HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique(), "country", "España")));
    }

    private long newAudit(String token, long farmId, String leadAuditor) {
        return id(call(token, HttpMethod.POST, "/api/audits", Map.of("farmId", farmId,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", "INTERNAL", "leadAuditor", leadAuditor)));
    }

    private List<Long> controlPoints(String token, long auditId) {
        List<Long> out = new ArrayList<>();
        call(token, HttpMethod.GET, "/api/audits/" + auditId + "/results", null).getBody()
                .forEach(i -> out.add(i.get("controlPointId").asLong()));
        return out;
    }

    private JsonNode trace(String token, String path) {
        ResponseEntity<JsonNode> r = call(token, HttpMethod.GET, path, null);
        assertThat(r.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return r.getBody().get("content");
    }

    private boolean anyEntry(JsonNode entries, String text) {
        for (JsonNode e : entries) {
            if (e.get("summary").asText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode entryWith(JsonNode entries, String text) {
        for (JsonNode e : entries) {
            if (e.get("summary").asText().contains(text)) {
                return e;
            }
        }
        throw new AssertionError("No hay una entrada que contenga «" + text + "»");
    }

    // ---------------------------------------------------------------- qué se anota y con qué detalle

    @Test
    void everyImportantChangeLeavesAnEntryWithOldAndNewValues() {
        long farm = newFarm(admin);
        long audit = newAudit(admin, farm, "Auditora Líder");
        long cp = controlPoints(admin, audit).get(0);

        assertThat(call(admin, HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results",
                List.of(Map.of("controlPointId", cp, "compliance", "COMPLIANT")))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(admin, HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results",
                List.of(Map.of("controlPointId", cp, "compliance", "NON_COMPLIANT", "evidenceNotes", "Sin registro"))))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        long finding = id(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/findings", Map.of(
                "controlPointId", cp, "classification", "MINOR", "title", "Falta registro")));
        assertThat(call(admin, HttpMethod.PUT, "/api/audits/" + audit + "/findings/" + finding, Map.of(
                "controlPointId", cp, "classification", "MAJOR", "title", "Falta registro", "status", "IN_PROGRESS"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        long ca = id(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/findings/" + finding
                + "/corrective-actions", Map.of("rootCause", "Olvido", "correction", "Registrar")));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(admin);
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(Base64.getDecoder().decode(PNG_1X1)) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        });
        assertThat(rest.exchange("/api/audits/" + audit + "/findings/" + finding + "/attachments", HttpMethod.POST,
                new HttpEntity<>(parts, h), JsonNode.class).getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode entries = trace(admin, "/api/audits/" + audit + "/trace");

        assertThat(anyEntry(entries, "Creó la auditoría")).isTrue();
        // Valor anterior y nuevo del resultado del punto.
        JsonNode result = entryWith(entries, "Marcó el punto");
        assertThat(result.get("changes").toString()).contains("Resultado").contains("Conforme")
                .contains("No conforme");
        // Cambio de clasificación y de estado de la NC.
        JsonNode update = entryWith(entries, "Cambió el estado de la no conformidad");
        assertThat(update.get("changes").toString()).contains("Abierta").contains("En curso").contains("Mayor")
                .contains("Menor");
        assertThat(anyEntry(entries, "Añadió una acción correctiva")).isTrue();
        assertThat(anyEntry(entries, "Subió el adjunto «foto.png»")).isTrue();
        // Autor y rol de cada entrada, y huella de 128 caracteres.
        JsonNode first = entries.get(0);
        assertThat(first.get("username").asText()).isEqualTo("admin@demo.test");
        assertThat(first.get("userRole").asText()).isEqualTo("ADMIN");
        assertThat(first.get("hash").asText()).hasSize(128);
        assertThat(first.get("signature").asText()).hasSize(128);
        assertThat(first.get("createdAt").asText()).isNotBlank();

        // Historial de la NC: sus cambios y los de su acción correctiva.
        JsonNode findingTrace = trace(admin, "/api/audits/" + audit + "/findings/" + finding + "/trace");
        assertThat(anyEntry(findingTrace, "Creó la no conformidad")).isTrue();
        assertThat(anyEntry(findingTrace, "Añadió una acción correctiva")).isTrue();
        assertThat(anyEntry(findingTrace, "Creó la auditoría")).isFalse();
        assertThat(ca).isPositive();
    }

    @Test
    void failedOperationsLeaveNoEntry() {
        long farm = newFarm(admin);
        long audit = newAudit(admin, farm, "Auditora Líder");
        int before = trace(admin, "/api/audits/" + audit + "/trace").size();

        // Cerrar sin haber evaluado todos los puntos es un conflicto: no debe quedar constancia de un cierre.
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "CLOSING"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(trace(admin, "/api/audits/" + audit + "/trace")).hasSize(before);
    }

    // ---------------------------------------------------------------- quién la ve

    @Test
    void visibilityDependsOnTheRole() {
        String ana = userWithRole("AUDITOR", "Ana Trazas " + unique());
        String beto = userWithRole("AUDITOR", "Beto Trazas " + unique());
        String viewer = userWithRole("VIEWER", "Lector Trazas");
        String quality = userWithRole("QUALITY", "Calidad Trazas");

        long farm = newFarm(ana);
        long anaAudit = newAudit(ana, farm, "Otra persona");
        long betoAudit = newAudit(beto, newFarm(beto), "Otra persona");

        // La auditora ve lo suyo y no lo de otra auditora.
        assertThat(call(ana, HttpMethod.GET, "/api/audits/" + anaAudit + "/trace", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(ana, HttpMethod.GET, "/api/audits/" + betoAudit + "/trace", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        for (JsonNode e : trace(ana, "/api/trace?size=200")) {
            assertThat(e.get("auditId").isNull() ? -1 : e.get("auditId").asLong()).isNotEqualTo(betoAudit);
        }
        // El lector no ve nada.
        assertThat(call(viewer, HttpMethod.GET, "/api/trace", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.GET, "/api/audits/" + anaAudit + "/trace", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // Administración y calidad ven toda la organización.
        for (String token : List.of(admin, quality)) {
            JsonNode all = trace(token, "/api/trace?size=200");
            assertThat(anyOfAudit(all, anaAudit)).isTrue();
            assertThat(anyOfAudit(all, betoAudit)).isTrue();
        }
        // Solo administración y calidad verifican, exportan y consultan sellos.
        assertThat(call(ana, HttpMethod.GET, "/api/trace/verify", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(ana, HttpMethod.GET, "/api/trace/export", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(quality, HttpMethod.GET, "/api/trace/verify", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(quality, HttpMethod.POST, "/api/trace/seals", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private boolean anyOfAudit(JsonNode entries, long auditId) {
        for (JsonNode e : entries) {
            if (!e.get("auditId").isNull() && e.get("auditId").asLong() == auditId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void anAuditorSeesTheAuditsWhereTheyAreLeadAuditor() {
        String lead = "Lidia Líder " + unique();
        String lidia = userWithRole("AUDITOR", lead);
        long audit = newAudit(admin, newFarm(admin), lead);

        assertThat(call(lidia, HttpMethod.GET, "/api/audits/" + audit + "/trace", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- rol Calidad

    @Test
    void qualityVerifiesAndClosesButDoesNotEdit() {
        String quality = userWithRole("QUALITY", "Calidad Flujo");
        long farm = newFarm(admin);
        long audit = newAudit(admin, farm, "Auditora Líder");
        List<Long> cps = controlPoints(admin, audit);
        List<Map<String, Object>> all = new ArrayList<>();
        cps.forEach(cp -> all.add(Map.of("controlPointId", cp, "compliance", "COMPLIANT")));
        call(admin, HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results", all));
        long finding = id(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/findings", Map.of(
                "controlPointId", cps.get(0), "classification", "MINOR", "title", "NC de prueba")));
        long ca = id(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/findings/" + finding
                + "/corrective-actions", Map.of("rootCause", "Causa", "correction", "Corrección")));

        // No edita: ni crea NC ni modifica el checklist, y no puede iniciar ni cerrar trámites que no son suyos.
        assertThat(call(quality, HttpMethod.POST, "/api/audits/" + audit + "/findings", Map.of(
                "controlPointId", cps.get(0), "classification", "MINOR", "title", "x")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(quality, HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results", all))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(quality, HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "CLOSING"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Verifica la acción correctiva: la NC se cierra sola y queda constancia de quién la verificó.
        assertThat(call(quality, HttpMethod.POST, "/api/audits/" + audit + "/findings/" + finding
                + "/corrective-actions/" + ca + "/verify", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode entries = trace(admin, "/api/audits/" + audit + "/findings/" + finding + "/trace");
        JsonNode closed = entryWith(entries, "pasó de");
        assertThat(closed.get("summary").asText()).contains("Cerrada");
        assertThat(entryWith(entries, "Cambió la acción correctiva").get("userRole").asText()).isEqualTo("QUALITY");

        // Envía y cierra la auditoría (los pasos previos los da el auditor o la administración).
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "CLOSING"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(quality, HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "SUBMITTED"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(quality, HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "CLOSED"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- inalterable y a prueba de manipulaciones

    @Test
    void theLogCannotBeModifiedOrDeleted() {
        newAudit(admin, newFarm(admin), "Auditora Líder");

        assertThatThrownBy(() -> jdbc.update("UPDATE trace.change_log SET summary = 'otra cosa'"))
                .hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM trace.change_log")).hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE trace.change_log")).hasMessageContaining("inalterable");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE trace.seal")).hasMessageContaining("inalterable");
    }

    @Test
    void theChainIsVerifiedAndAnyTamperingIsDetected() {
        newAudit(admin, newFarm(admin), "Auditora Líder");
        newAudit(admin, newFarm(admin), "Auditora Líder");

        JsonNode ok = call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody();
        assertThat(ok.get("ok").asBoolean()).as(ok.toString()).isTrue();

        // Se manipula el texto de una entrada saltándose el bloqueo (un administrador de la base de datos).
        Map<String, Object> victim = jdbc.queryForMap("SELECT id, seq, summary FROM trace.change_log "
                + "ORDER BY seq DESC LIMIT 1 OFFSET 1");
        long seq = ((Number) victim.get("seq")).longValue();
        String original = (String) victim.get("summary");
        try {
            withoutImmutability(() -> jdbc.update("UPDATE trace.change_log SET summary = 'Texto cambiado' WHERE id = ?",
                    victim.get("id")));
            JsonNode broken = call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody();
            assertThat(broken.get("ok").asBoolean()).isFalse();
            assertThat(broken.get("firstBrokenSeq").asLong()).isEqualTo(seq);
            assertThat(broken.get("message").asText()).contains(String.valueOf(seq));
        } finally {
            withoutImmutability(() -> jdbc.update("UPDATE trace.change_log SET summary = ? WHERE id = ?", original,
                    victim.get("id")));
        }
        assertThat(call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody().get("ok").asBoolean()).isTrue();
    }

    @Test
    void aDeletedEntryIsDetectedAndSealsAnchorTheChain() {
        newAudit(admin, newFarm(admin), "Auditora Líder");
        ResponseEntity<JsonNode> sealed = call(admin, HttpMethod.POST, "/api/trace/seals", null);
        assertThat(sealed.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        // Sin entradas nuevas no se vuelve a sellar.
        ResponseEntity<JsonNode> again = call(admin, HttpMethod.POST, "/api/trace/seals", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode report = call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody();
        assertThat(report.get("ok").asBoolean()).isTrue();
        assertThat(report.get("sealsChecked").asInt()).isPositive();

        // Se borra una entrada intermedia: la numeración se rompe.
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM trace.change_log ORDER BY seq LIMIT 1 OFFSET 1");
        long seq = ((Number) row.get("seq")).longValue();
        withoutImmutability(() -> jdbc.update("DELETE FROM trace.change_log WHERE id = ?", row.get("id")));
        try {
            JsonNode broken = call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody();
            assertThat(broken.get("ok").asBoolean()).isFalse();
            assertThat(broken.get("message").asText()).contains("Falta la entrada " + seq);
        } finally {
            withoutImmutability(() -> jdbc.update("INSERT INTO trace.change_log (id, organization_id, seq, "
                            + "occurred_at, created_at, user_id, username, user_full_name, user_role, entity_type, "
                            + "entity_id, entity_ref, parent_type, parent_id, audit_id, action, summary, changes, "
                            + "prev_hash, hash, signature, format_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    row.get("id"), row.get("organization_id"), row.get("seq"), row.get("occurred_at"),
                    row.get("created_at"), row.get("user_id"), row.get("username"), row.get("user_full_name"),
                    row.get("user_role"), row.get("entity_type"), row.get("entity_id"), row.get("entity_ref"),
                    row.get("parent_type"), row.get("parent_id"), row.get("audit_id"), row.get("action"),
                    row.get("summary"), row.get("changes"), row.get("prev_hash"), row.get("hash"),
                    row.get("signature"), row.get("format_version")));
        }
        assertThat(call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody().get("ok").asBoolean()).isTrue();

        ResponseEntity<String> seals = rest.exchange("/api/trace/seals/export", HttpMethod.GET,
                new HttpEntity<>(null, json(admin)), String.class);
        assertThat(seals.getBody()).contains("Sellos de trazabilidad");
    }

    private void withoutImmutability(Runnable action) {
        jdbc.execute("ALTER TABLE trace.change_log DISABLE TRIGGER trg_change_log_immutable");
        try {
            action.run();
        } finally {
            jdbc.execute("ALTER TABLE trace.change_log ENABLE TRIGGER trg_change_log_immutable");
        }
    }

    @Test
    void theCsvExportNeutralisesSpreadsheetFormulas() {
        long farm = id(call(admin, HttpMethod.POST, "/api/farms",
                Map.of("legalName", "=HYPERLINK(\"http://x\")", "country", "España")));
        assertThat(farm).isPositive();

        ResponseEntity<String> csv = rest.exchange("/api/trace/export", HttpMethod.GET,
                new HttpEntity<>(null, json(admin)), String.class);

        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getBody()).contains("huella SHA-512").contains("Creó la explotación");
        assertThat(csv.getBody()).doesNotContain("\"=HYPERLINK").contains("\"'=HYPERLINK");
    }
}
