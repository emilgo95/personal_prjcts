package com.agriaudit;

import com.agriaudit.findings.ClosureClock;
import com.agriaudit.support.TestPostgres;
import tools.jackson.databind.JsonNode;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reloj de cierre de NC (RF-301, RF-308) de extremo a extremo: fecha límite automática con el plazo del estándar
 * desde la fecha de la auditoría, fecha indicada a mano respetada, fecha de cierre al cerrarse y recuentos del panel.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class FindingDeadlineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-clock");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
    }

    @Autowired
    TestRestTemplate rest;

    private String token;
    private long auditId;
    private LocalDate auditDate;

    @BeforeEach
    void audit() {
        token = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "admin@demo.test", "password", "Admin123!"), headers()),
                JsonNode.class).getBody().get("accessToken").asText();
        long farmId = post("/api/farms", Map.of("legalName", "Finca Reloj S.L.")).get("id").asLong();
        auditDate = ClosureClock.today().minusDays(10);
        auditId = post("/api/audits", Map.of("farmId", farmId, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "SELF_ASSESSMENT", "actualDate", auditDate.toString())).get("id").asLong();
    }

    @Test
    void dueDateDefaultsTo28DaysFromTheAuditAndRespectsAManualDate() {
        JsonNode major = post(findings(), finding("MAJOR", null));
        assertThat(major.get("dueDate").asText()).isEqualTo(auditDate.plusDays(28).toString());

        JsonNode observation = post(findings(), finding("OBSERVATION", null));
        assertThat(observation.get("dueDate").isNull()).isTrue();

        JsonNode manual = post(findings(), finding("MINOR", "2027-01-15"));
        assertThat(manual.get("dueDate").asText()).isEqualTo("2027-01-15");

        // Al editar sin fecha (borrada a mano) se vuelve a calcular con el plazo de su gravedad
        Map<String, Object> edit = finding("MINOR", null);
        JsonNode edited = exchange(HttpMethod.PUT, findings() + "/" + manual.get("id").asLong(), edit);
        assertThat(edited.get("dueDate").asText()).isEqualTo(auditDate.plusDays(28).toString());
    }

    @Test
    void closingSetsTheClosedAtAndTheDashboardCountsItOnTime() {
        JsonNode nc = post(findings(), finding("MAJOR", null));
        long id = nc.get("id").asLong();
        assertThat(nc.get("closedAt").isNull()).isTrue();

        JsonNode before = get("/api/dashboard");
        long onTimeBefore = before.get("closedOnTimeFindings").asLong();
        long dueIn14Before = before.get("dueIn14Findings").asLong();
        assertThat(dueIn14Before).isGreaterThanOrEqualTo(0);

        // Una acción correctiva verificada cierra la NC
        post(findings() + "/" + id + "/corrective-actions", Map.of("correction", "Corregido", "status", "VERIFIED"));
        JsonNode closed = find(id);
        assertThat(closed).isNotNull();
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("closedAt").isNull()).isFalse();

        JsonNode after = get("/api/dashboard");
        assertThat(after.get("closedOnTimeFindings").asLong()).isEqualTo(onTimeBefore + 1);
        assertThat(after.get("closedWithDeadlineFindings").asLong())
                .isGreaterThanOrEqualTo(after.get("closedOnTimeFindings").asLong());
    }

    // ------------------------------------------------------------------ utilidades

    private String findings() {
        return "/api/audits/" + auditId + "/findings";
    }

    private static Map<String, Object> finding(String classification, String dueDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("classification", classification);
        body.put("title", "NC " + classification);
        body.put("dueDate", dueDate);
        return body;
    }

    private JsonNode find(long id) {
        for (JsonNode f : get(findings())) {
            if (f.get("id").asLong() == id) {
                return f;
            }
        }
        return null;
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        return h;
    }

    private JsonNode get(String path) {
        ResponseEntity<JsonNode> r = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers()), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private JsonNode post(String path, Map<String, ?> body) {
        ResponseEntity<JsonNode> r = rest.postForEntity(path, new HttpEntity<>(body, headers()), JsonNode.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as(path + " → " + r.getBody()).isTrue();
        return r.getBody();
    }

    private JsonNode exchange(HttpMethod method, String path, Map<String, ?> body) {
        ResponseEntity<JsonNode> r = rest.exchange(path, method, new HttpEntity<>(body, headers()), JsonNode.class);
        assertThat(r.getStatusCode()).as(path + " → " + r.getBody()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }
}
