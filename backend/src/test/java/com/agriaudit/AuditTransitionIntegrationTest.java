package com.agriaudit;

import com.agriaudit.support.TestPostgres;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo de estados de una auditoría: solo se avanza paso a paso, no se cierra el checklist con puntos sin
 * evaluar y no se cierra la auditoría con no conformidades abiertas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuditTransitionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-transitions");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.demo.seed", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    private String token;

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }

    private ResponseEntity<JsonNode> transition(long auditId, String status) {
        return rest.postForEntity("/api/audits/" + auditId + "/transition",
                new HttpEntity<>(Map.of("status", status), auth()), JsonNode.class);
    }

    private List<String> allowed(long auditId) {
        JsonNode body = rest.exchange("/api/audits/" + auditId, HttpMethod.GET, new HttpEntity<>(auth()),
                JsonNode.class).getBody();
        List<String> out = new ArrayList<>();
        body.get("allowedTransitions").forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void auditFollowsTheClosingWorkflow() {
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "admin@demo.test", "password", "Admin123!"), auth()), JsonNode.class);
        token = login.getBody().get("accessToken").asText();

        long farmId = rest.postForEntity("/api/farms",
                new HttpEntity<>(Map.of("legalName", "Finca Flujo S.L.", "country", "España"), auth()),
                JsonNode.class).getBody().get("id").asLong();
        long auditId = rest.postForEntity("/api/audits",
                new HttpEntity<>(Map.of("farmId", farmId, "standardCode", "GLOBALGAP_IFA_V6",
                        "auditType", "SELF_ASSESSMENT", "leadAuditor", "Ana Auditora"), auth()),
                JsonNode.class).getBody().get("id").asLong();

        // Borrador: solo se puede pasar a «En curso».
        assertThat(allowed(auditId)).containsExactly("IN_PROGRESS");
        assertThat(transition(auditId, "CLOSED").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(transition(auditId, "CLOSING").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(transition(auditId, "DRAFT").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transition(auditId, "NO_EXISTE").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> started = transition(auditId, "IN_PROGRESS");
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody().get("status").asText()).isEqualTo("IN_PROGRESS");

        // Con puntos sin evaluar no se puede pasar a cierre, y el mensaje dice cuántos faltan.
        ResponseEntity<JsonNode> early = transition(auditId, "CLOSING");
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(early.getBody().toString()).contains("puntos de control por evaluar");

        // Se evalúan todos los puntos como conformes.
        JsonNode items = rest.exchange("/api/audits/" + auditId + "/results", HttpMethod.GET,
                new HttpEntity<>(auth()), JsonNode.class).getBody();
        List<Map<String, Object>> all = new ArrayList<>();
        long firstControlPoint = items.get(0).get("controlPointId").asLong();
        items.forEach(i -> all.add(Map.of("controlPointId", i.get("controlPointId").asLong(),
                "compliance", "COMPLIANT")));
        assertThat(rest.exchange("/api/audits/" + auditId + "/results", HttpMethod.PUT,
                new HttpEntity<>(Map.of("results", all), auth()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(transition(auditId, "CLOSING").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed(auditId)).containsExactlyInAnyOrder("SUBMITTED", "IN_PROGRESS");

        // Se registra una no conformidad abierta y se envía el expediente.
        long findingId = rest.postForEntity("/api/audits/" + auditId + "/findings",
                new HttpEntity<>(Map.of("controlPointId", firstControlPoint, "classification", "MAJOR",
                        "title", "NC de prueba", "dueDate", "2026-12-31"), auth()),
                JsonNode.class).getBody().get("id").asLong();
        assertThat(transition(auditId, "SUBMITTED").getStatusCode()).isEqualTo(HttpStatus.OK);

        // No se puede cerrar con la no conformidad abierta...
        ResponseEntity<JsonNode> blocked = transition(auditId, "CLOSED");
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody().toString()).contains("no conformidad");

        // ...hay que reabrirla, verificar la acción correctiva (la NC se cierra sola) y volver a enviarla.
        assertThat(transition(auditId, "CLOSING").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.postForEntity("/api/audits/" + auditId + "/findings/" + findingId + "/corrective-actions",
                new HttpEntity<>(Map.of("rootCause", "Causa", "correction", "Corrección", "preventiveAction", "Prevención",
                        "responsiblePerson", "Calidad", "status", "VERIFIED"), auth()), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transition(auditId, "SUBMITTED").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> closed = transition(auditId, "CLOSED");
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closed.getBody().get("allowedTransitions")).isEmpty();

        // Una auditoría cerrada es definitiva.
        assertThat(transition(auditId, "SUBMITTED").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
