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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditorías › No conformidades: todas las NC de la organización, con vistas que cuentan exactamente lo mismo que las
 * cifras del panel (abiertas, vencidas, vencen en 2/7/14 días, cerradas en 12 meses).
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class FindingListIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-findlist");
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

    private long finding(long audit, long cp, String title, LocalDate due) {
        return id(call(HttpMethod.POST, "/api/audits/" + audit + "/findings", Map.of("controlPointId", cp,
                "classification", "MINOR", "title", title, "dueDate", due.toString())));
    }

    /** Cierra la NC verificando una acción correctiva completada (queda cerrada hoy). */
    private void close(long audit, long finding) {
        String base = "/api/audits/" + audit + "/findings/" + finding + "/corrective-actions";
        long ca = id(call(HttpMethod.POST, base, Map.of("correction", "Hecho", "status", "COMPLETED")));
        assertThat(call(HttpMethod.POST, base + "/" + ca + "/verify", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode list(String view) {
        ResponseEntity<JsonNode> r = call(HttpMethod.GET, "/api/findings" + (view == null ? "" : "?view=" + view), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private List<String> titles(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("rows").forEach(r -> out.add(r.get("title").asText()));
        return out;
    }

    @Test
    void theListShowsEveryFindingOfTheOrganizationAndEachViewCountsTheSameAsThePanel() {
        long farm = id(call(HttpMethod.POST, "/api/farms",
                Map.of("legalName", "Finca " + UUID.randomUUID().toString().substring(0, 6), "country", "España")));
        long audit = id(call(HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "INTERNAL", "leadAuditor", "Ana Auditora")));
        List<Long> cps = new ArrayList<>();
        call(HttpMethod.GET, "/api/audits/" + audit + "/results", null).getBody()
                .forEach(i -> cps.add(i.get("controlPointId").asLong()));
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        finding(audit, cps.get(0), "Vencida", today.minusDays(3));
        finding(audit, cps.get(1), "Vence mañana", today.plusDays(1));
        finding(audit, cps.get(2), "Vence en 10 días", today.plusDays(10));
        finding(audit, cps.get(3), "Vence en 20 días", today.plusDays(20));
        long onTime = finding(audit, cps.get(4), "Cerrada a tiempo", today.plusDays(5));
        long late = finding(audit, cps.get(5), "Cerrada tarde", today.minusDays(2));
        close(audit, onTime);
        close(audit, late);

        // Las mismas cifras que el panel.
        JsonNode panel = call(HttpMethod.GET, "/api/dashboard", null).getBody();
        JsonNode counts = list(null).get("counts");
        assertThat(counts.get("OPEN").asLong()).isEqualTo(panel.get("openFindings").asLong()).isEqualTo(4);
        assertThat(counts.get("OVERDUE").asLong()).isEqualTo(panel.get("overdueFindings").asLong()).isEqualTo(1);
        assertThat(counts.get("DUE_2").asLong()).isEqualTo(panel.get("dueIn2Findings").asLong()).isEqualTo(1);
        assertThat(counts.get("DUE_7").asLong()).isEqualTo(panel.get("dueSoonFindings").asLong()).isEqualTo(1);
        assertThat(counts.get("DUE_14").asLong()).isEqualTo(panel.get("dueIn14Findings").asLong()).isEqualTo(2);
        assertThat(counts.get("CLOSED_12M").asLong()).isEqualTo(panel.get("closedWithDeadlineFindings").asLong()).isEqualTo(2);
        assertThat(counts.get("CLOSED_LATE").asLong()).isEqualTo(1);
        assertThat(counts.get("ALL").asLong()).isEqualTo(6);

        // Por defecto, las abiertas de la más urgente a la menos, con su auditoría y explotación.
        JsonNode open = list(null);
        assertThat(open.get("view").asText()).isEqualTo("OPEN");
        assertThat(titles(open)).containsExactly("Vencida", "Vence mañana", "Vence en 10 días", "Vence en 20 días");
        JsonNode first = open.get("rows").get(0);
        assertThat(first.get("auditId").asLong()).isEqualTo(audit);
        assertThat(first.get("auditReference").asText()).startsWith("AUD-");
        assertThat(first.get("farmName").asText()).startsWith("Finca ");
        assertThat(first.get("daysLeft").asLong()).isEqualTo(-3);
        assertThat(first.get("controlPointRef").asText()).isNotBlank();

        assertThat(titles(list("overdue"))).containsExactly("Vencida");
        assertThat(titles(list("DUE_14"))).containsExactly("Vence mañana", "Vence en 10 días");
        JsonNode closed = list("CLOSED_12M");
        assertThat(titles(closed)).containsExactlyInAnyOrder("Cerrada a tiempo", "Cerrada tarde");
        closed.get("rows").forEach(r -> assertThat(r.get("closedOnTime").asBoolean())
                .isEqualTo("Cerrada a tiempo".equals(r.get("title").asText())));
        assertThat(titles(list("CLOSED_LATE"))).containsExactly("Cerrada tarde");

        assertThat(call(HttpMethod.GET, "/api/findings?view=RARA", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
