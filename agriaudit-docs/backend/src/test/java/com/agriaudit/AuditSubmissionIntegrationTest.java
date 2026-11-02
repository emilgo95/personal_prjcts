package com.agriaudit;

import com.agriaudit.support.TestPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enviar una auditoría: no se puede con NC por revisar (acción completada sin verificar), y cada NC vencida y sin
 * cerrar necesita una justificación del retraso, que queda en la NC, en la traza y en los documentos. Las NC
 * abiertas dentro de plazo no impiden el envío.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuditSubmissionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-submit");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.demo.seed", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    private String token;

    @BeforeEach
    void loginAsAdmin() {
        ResponseEntity<JsonNode> r = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "admin@demo.test", "password", "Admin123!"), headers()), JsonNode.class);
        token = r.getBody().get("accessToken").asText();
    }

    // ---------------------------------------------------------------- utilidades

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers()), JsonNode.class);
    }

    private long id(ResponseEntity<JsonNode> r) {
        assertThat(r.getStatusCode().is2xxSuccessful()).as("respuesta %s: %s", r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody().get("id").asLong();
    }

    private List<Long> controlPoints(long audit) {
        List<Long> out = new ArrayList<>();
        call(HttpMethod.GET, "/api/audits/" + audit + "/results", null).getBody()
                .forEach(i -> out.add(i.get("controlPointId").asLong()));
        return out;
    }

    private long auditInClosing() {
        long farm = id(call(HttpMethod.POST, "/api/farms",
                Map.of("legalName", "Finca " + UUID.randomUUID().toString().substring(0, 8), "country", "España")));
        long audit = id(call(HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "INTERNAL", "leadAuditor", "Ana Auditora")));
        List<Map<String, Object>> all = new ArrayList<>();
        controlPoints(audit).forEach(cp -> all.add(Map.of("controlPointId", cp, "compliance", "COMPLIANT")));
        assertThat(call(HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results", all)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return audit;
    }

    private long finding(long audit, long cp, String title, LocalDate due) {
        return id(call(HttpMethod.POST, "/api/audits/" + audit + "/findings", Map.of("controlPointId", cp,
                "classification", "MINOR", "title", title, "dueDate", due.toString())));
    }

    private ResponseEntity<JsonNode> submit(long audit, Map<Long, String> justifications) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "SUBMITTED");
        if (justifications != null) {
            body.put("lateJustifications", justifications);
        }
        return call(HttpMethod.POST, "/api/audits/" + audit + "/transition", body);
    }

    private JsonNode findingJson(long audit, long finding) {
        for (JsonNode f : call(HttpMethod.GET, "/api/audits/" + audit + "/findings", null).getBody()) {
            if (f.get("id").asLong() == finding) {
                return f;
            }
        }
        throw new AssertionError("No está la NC " + finding);
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void anAuditCannotBeSentWithFindingsToReviewAndOverdueOnesNeedAJustification() {
        long audit = auditInClosing();
        List<Long> cps = controlPoints(audit);
        LocalDate today = LocalDate.now();
        long overdue = finding(audit, cps.get(0), "Vencida", today.minusDays(5));
        long toReview = finding(audit, cps.get(1), "Hecha sin verificar", today.plusDays(10));
        long inTime = finding(audit, cps.get(2), "Abierta en plazo", today.plusDays(20));
        long ca = id(call(HttpMethod.POST, "/api/audits/" + audit + "/findings/" + toReview + "/corrective-actions",
                Map.of("correction", "Limpieza registrada", "status", "COMPLETED")));
        assertThat(call(HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "CLOSING"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // Lo que la pantalla consulta antes de enviar.
        JsonNode check = call(HttpMethod.GET, "/api/audits/" + audit + "/submission-check", null).getBody();
        assertThat(check.get("toReview")).hasSize(1);
        assertThat(check.get("toReview").get(0).get("id").asLong()).isEqualTo(toReview);
        assertThat(check.get("overdue")).hasSize(1);
        assertThat(check.get("overdue").get(0).get("id").asLong()).isEqualTo(overdue);

        // Con una NC por revisar no se envía, ni aunque se justifiquen las vencidas.
        ResponseEntity<JsonNode> blocked = submit(audit, Map.of(overdue, "Pendiente del laboratorio"));
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody().get("message").asText()).contains("por revisar")
                .contains(findingJson(audit, toReview).get("reference").asText());
        assertThat(findingJson(audit, overdue).get("lateJustification").isNull()).isTrue();

        // Verificada la acción, la NC se cierra y deja de bloquear; ahora falta justificar la vencida.
        assertThat(call(HttpMethod.POST, "/api/audits/" + audit + "/findings/" + toReview + "/corrective-actions/"
                + ca + "/verify", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> unjustified = submit(audit, null);
        assertThat(unjustified.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(unjustified.getBody().get("message").asText()).contains("vencida sin justificar")
                .contains(findingJson(audit, overdue).get("reference").asText());
        // Justificación vacía o de una NC que no está vencida: no vale.
        assertThat(submit(audit, Map.of(overdue, "   ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(submit(audit, Map.of(inTime, "No hace falta")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(submit(audit, Map.of(overdue, "x".repeat(2001))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Justificada, se envía (la abierta en plazo no lo impide) y la justificación queda en la NC y en la traza.
        ResponseEntity<JsonNode> sent = submit(audit, Map.of(overdue, "Pendiente del análisis del laboratorio; plan comunicado"));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sent.getBody().get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(findingJson(audit, overdue).get("lateJustification").asText()).startsWith("Pendiente del análisis");
        assertThat(findingJson(audit, inTime).get("lateJustification").isNull()).isTrue();
        assertThat(call(HttpMethod.GET, "/api/audits/" + audit + "/trace?size=100", null).getBody().toString())
                .contains("Justificó el retraso");

        // Reabierta y enviada de nuevo, la justificación ya guardada sirve.
        assertThat(call(HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "CLOSING"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submit(audit, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anAuditWithoutPendingFindingsIsSentAsBefore() {
        long audit = auditInClosing();
        long cp = controlPoints(audit).get(0);
        finding(audit, cp, "Abierta en plazo", LocalDate.now().plusDays(28));
        assertThat(call(HttpMethod.POST, "/api/audits/" + audit + "/transition", Map.of("status", "CLOSING"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode check = call(HttpMethod.GET, "/api/audits/" + audit + "/submission-check", null).getBody();
        assertThat(check.get("toReview")).isEmpty();
        assertThat(check.get("overdue")).isEmpty();
        assertThat(submit(audit, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
