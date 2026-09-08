package com.agriaudit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
    }

    @Autowired
    TestRestTemplate rest;

    private String token;

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return h;
    }

    @Test
    void fullAuditLifecycleProducesDownloadableDocuments() {
        // --- login ---
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "admin@demo.test", "password", "Admin123!"), auth()),
                JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = login.getBody().get("accessToken").asText();
        assertThat(token).isNotBlank();

        // --- protected endpoint sin token -> 401 ---
        ResponseEntity<String> noAuth = rest.exchange("/api/farms", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // --- crear explotación ---
        ResponseEntity<JsonNode> farm = rest.postForEntity("/api/farms",
                new HttpEntity<>(Map.of("legalName", "Finca Demo S.L.", "country", "España",
                        "crops", "Tomate, Pimiento", "registrationNumber", "4049928000000"), auth()),
                JsonNode.class);
        assertThat(farm.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long farmId = farm.getBody().get("id").asLong();

        // --- crear auditoría ---
        ResponseEntity<JsonNode> audit = rest.postForEntity("/api/audits",
                new HttpEntity<>(Map.of("farmId", farmId, "standardCode", "GLOBALGAP_IFA_V6",
                        "auditType", "SELF_ASSESSMENT", "leadAuditor", "Ana Auditora"), auth()),
                JsonNode.class);
        assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long auditId = audit.getBody().get("id").asLong();
        assertThat(audit.getBody().get("reference").asText()).startsWith("AUD-");

        // --- resultados del checklist ---
        ResponseEntity<List<Map<String, Object>>> results = rest.exchange(
                "/api/audits/" + auditId + "/results", HttpMethod.GET, new HttpEntity<>(auth()),
                new ParameterizedTypeReference<>() {});
        List<Map<String, Object>> items = results.getBody();
        assertThat(items).isNotEmpty();

        long cpCompliant = ((Number) items.get(0).get("controlPointId")).longValue();
        long cpNonCompliant = ((Number) items.get(1).get("controlPointId")).longValue();
        var payload = Map.of("results", List.of(
                Map.of("controlPointId", cpCompliant, "compliance", "COMPLIANT", "evidenceNotes", "Prueba OK"),
                Map.of("controlPointId", cpNonCompliant, "compliance", "NON_COMPLIANT", "evidenceNotes", "Falta")));
        ResponseEntity<String> put = rest.exchange("/api/audits/" + auditId + "/results",
                HttpMethod.PUT, new HttpEntity<>(payload, auth()), String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);

        // --- hallazgo + acción correctiva ---
        ResponseEntity<JsonNode> finding = rest.postForEntity("/api/audits/" + auditId + "/findings",
                new HttpEntity<>(Map.of("controlPointId", cpNonCompliant, "classification", "MAJOR",
                        "title", "No conformidad de prueba", "description", "Descripción",
                        "dueDate", "2026-12-31"), auth()),
                JsonNode.class);
        assertThat(finding.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long findingId = finding.getBody().get("id").asLong();

        ResponseEntity<JsonNode> ca = rest.postForEntity(
                "/api/audits/" + auditId + "/findings/" + findingId + "/corrective-actions",
                new HttpEntity<>(Map.of("rootCause", "Causa", "correction", "Corrección",
                        "preventiveAction", "Preventiva", "responsiblePerson", "Responsable",
                        "status", "IN_PROGRESS"), auth()),
                JsonNode.class);
        assertThat(ca.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long caId = ca.getBody().get("id").asLong();

        // --- editar el hallazgo ---
        ResponseEntity<JsonNode> updated = rest.exchange("/api/audits/" + auditId + "/findings/" + findingId,
                HttpMethod.PUT, new HttpEntity<>(Map.of("controlPointId", cpNonCompliant,
                        "classification", "CRITICAL", "title", "NC actualizada",
                        "description", "Nueva descripción", "status", "IN_PROGRESS"), auth()), JsonNode.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("classification").asText()).isEqualTo("CRITICAL");

        // --- borrar la acción correctiva ---
        ResponseEntity<Void> deletedCa = rest.exchange(
                "/api/audits/" + auditId + "/findings/" + findingId + "/corrective-actions/" + caId,
                HttpMethod.DELETE, new HttpEntity<>(auth()), Void.class);
        assertThat(deletedCa.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // --- adjuntar una imagen de evidencia a un punto de control ---
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        org.springframework.util.LinkedMultiValueMap<String, Object> parts = new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("file", new org.springframework.core.io.ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "evidencia.png";
            }
        });
        HttpHeaders mh = new HttpHeaders();
        mh.setContentType(MediaType.MULTIPART_FORM_DATA);
        mh.setBearerAuth(token);
        ResponseEntity<JsonNode> att = rest.postForEntity(
                "/api/audits/" + auditId + "/results/" + cpNonCompliant + "/attachments",
                new HttpEntity<>(parts, mh), JsonNode.class);
        assertThat(att.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(att.getBody().get("image").asBoolean()).isTrue();
        long attId = att.getBody().get("id").asLong();

        ResponseEntity<byte[]> attFile = rest.exchange("/api/attachments/" + attId + "/download",
                HttpMethod.GET, new HttpEntity<>(auth()), byte[].class);
        assertThat(attFile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attFile.getBody()).isNotEmpty();

        // el adjunto aparece en los resultados del checklist
        ResponseEntity<JsonNode> withAtt = rest.exchange("/api/audits/" + auditId + "/results",
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        boolean found = false;
        for (JsonNode item : withAtt.getBody()) {
            if (item.get("controlPointId").asLong() == cpNonCompliant) {
                found = item.get("attachments").size() == 1;
            }
        }
        assertThat(found).as("el adjunto aparece en el resultado del punto de control").isTrue();

        // --- score ---
        ResponseEntity<JsonNode> score = rest.exchange("/api/audits/" + auditId + "/score",
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        assertThat(score.getBody().get("overallResult").asText()).isIn("PASS", "FAIL", "PENDING", "REPORT_ONLY");

        // --- generar y descargar documentos ---
        String[][] docs = {
                {"CHECKLIST", "XLSX"},
                {"NONCONFORMITY_REPORT", "DOCX"},
                {"AUDIT_REPORT", "PDF"},
                {"CAPA_REPORT", "XLSX"},
                {"AUDIT_PLAN", "PDF"},
        };
        for (String[] d : docs) {
            ResponseEntity<JsonNode> gen = rest.postForEntity(
                    "/api/audits/" + auditId + "/documents/generate",
                    new HttpEntity<>(Map.of("documentType", d[0], "format", d[1]), auth()), JsonNode.class);
            assertThat(gen.getStatusCode()).as("generar %s/%s", d[0], d[1]).isEqualTo(HttpStatus.CREATED);
            long docId = gen.getBody().get("id").asLong();
            assertThat(gen.getBody().get("sizeBytes").asLong()).isPositive();

            ResponseEntity<byte[]> file = rest.exchange("/api/documents/" + docId + "/download",
                    HttpMethod.GET, new HttpEntity<>(auth()), byte[].class);
            assertThat(file.getStatusCode()).isEqualTo(HttpStatus.OK);
            byte[] body = file.getBody();
            assertThat(body).isNotEmpty();
            if (d[1].equals("PDF")) {
                assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
            } else {
                assertThat(body[0]).isEqualTo((byte) 'P');
                assertThat(body[1]).isEqualTo((byte) 'K');
            }
        }
    }
}
