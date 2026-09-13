package com.agriaudit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import com.agriaudit.support.TestPostgres;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento multitenant a nivel de objeto (RS-AUTHZ-02, IDOR/BOLA): un usuario de otra organización
 * que conoce los identificadores no puede leer ni modificar explotaciones, auditorías, no conformidades,
 * acciones correctivas ni adjuntos ajenos. Test transversal que crece con cada entidad nueva de la Fase 1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TenantIsolationIntegrationTest {

    private static final String OTHER_EMAIL = "intruso@otra-org.test";
    private static final String OTHER_PASSWORD = "Intruso123!";

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-tenant");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    private HttpHeaders owner;
    private HttpHeaders intruder;

    @BeforeEach
    void users() {
        Long otherOrg = jdbc.queryForObject("INSERT INTO organizations (name) VALUES ('Otra Cooperativa') "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id", Long.class);
        jdbc.update("INSERT INTO app_users (organization_id, email, password_hash, full_name, role) "
                        + "VALUES (?, ?, ?, 'Intruso', 'AUDITOR') ON CONFLICT (email) DO NOTHING",
                otherOrg, OTHER_EMAIL, passwordEncoder.encode(OTHER_PASSWORD));
        owner = login("admin@demo.test", "Admin123!");
        intruder = login(OTHER_EMAIL, OTHER_PASSWORD);
    }

    @Test
    void otraOrganizacionNoAccedeAObjetosAjenosAunqueConozcaLosIds() {
        // --- datos de la organización propietaria ---
        long farmId = post("/api/farms", owner, Map.of("legalName", "Finca Propia S.L.")).get("id").asLong();
        long auditId = post("/api/audits", owner, Map.of("farmId", farmId, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "INTERNAL")).get("id").asLong();
        long findingId = post("/api/audits/" + auditId + "/findings", owner,
                Map.of("classification", "MINOR", "title", "NC propia")).get("id").asLong();
        String caPath = "/api/audits/" + auditId + "/findings/" + findingId + "/corrective-actions";
        long caId = post(caPath, owner, Map.of("rootCause", "Causa")).get("id").asLong();
        long attachmentId = uploadPng("/api/audits/" + auditId + "/findings/" + findingId + "/attachments", owner);

        // --- lecturas desde otra organización ---
        assertStatus(HttpMethod.GET, "/api/farms/" + farmId, intruder, null, HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.GET, "/api/audits/" + auditId, intruder, null, HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.GET, "/api/audits/" + auditId + "/results", intruder, null, HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.GET, "/api/audits/" + auditId + "/findings", intruder, null, HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.GET, "/api/audits/" + auditId + "/findings/" + findingId + "/attachments",
                intruder, null, HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.GET, "/api/attachments/" + attachmentId + "/download", intruder, null,
                HttpStatus.NOT_FOUND);

        // --- escrituras desde otra organización ---
        assertStatus(HttpMethod.PUT, "/api/audits/" + auditId + "/findings/" + findingId, intruder,
                Map.of("classification", "MAJOR", "title", "Manipulada"), HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.PUT, caPath + "/" + caId, intruder, Map.of("rootCause", "Manipulada"),
                HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.DELETE, caPath + "/" + caId, intruder, null, HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.DELETE, "/api/attachments/" + attachmentId, intruder, null, HttpStatus.NOT_FOUND);
        assertStatus(HttpMethod.DELETE, "/api/audits/" + auditId + "/findings/" + findingId, intruder, null,
                HttpStatus.NOT_FOUND);

        // --- y todo sigue intacto para su propietario ---
        JsonNode findings = rest.exchange("/api/audits/" + auditId + "/findings", HttpMethod.GET,
                new HttpEntity<>(owner), JsonNode.class).getBody();
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).get("title").asText()).isEqualTo("NC propia");
        assertThat(findings.get(0).get("correctiveActions")).hasSize(1);
        assertThat(findings.get(0).get("attachments")).hasSize(1);
    }

    // ---- helpers ----

    private HttpHeaders login(String email, String password) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), h), JsonNode.class);
        assertThat(login.getStatusCode()).as("login de %s", email).isEqualTo(HttpStatus.OK);
        h.setBearerAuth(login.getBody().get("accessToken").asText());
        return h;
    }

    private JsonNode post(String path, HttpHeaders headers, Map<String, ?> body) {
        ResponseEntity<JsonNode> r = rest.postForEntity(path, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(r.getStatusCode()).as("POST %s", path).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private long uploadPng(String path, HttpHeaders auth) {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "evidencia.png";
            }
        });
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(auth.getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length()));
        ResponseEntity<JsonNode> r = rest.postForEntity(path, new HttpEntity<>(parts, h), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id").asLong();
    }

    private void assertStatus(HttpMethod method, String path, HttpHeaders headers, Object body, HttpStatus expected) {
        ResponseEntity<String> r = rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        assertThat(r.getStatusCode()).as("%s %s desde otra organización", method, path).isEqualTo(expected);
    }
}
