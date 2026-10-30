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
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Buscador de la lista de auditorías: busca en todas las páginas y en los datos de la explotación y el estándar. */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuditSearchIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-search");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.demo.seed", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    private String token;

    @BeforeEach
    void loginAsAdmin() {
        ResponseEntity<JsonNode> r = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "admin@demo.test", "password", "Admin123!"), headers(null)),
                JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = r.getBody().get("accessToken").asText();
    }

    // ---------------------------------------------------------------- utilidades

    private HttpHeaders headers(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            h.setBearerAuth(bearer);
        }
        return h;
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private long newFarm(String legalName, String city) {
        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/farms",
                Map.of("legalName", legalName, "city", city, "country", "España"));
        assertThat(r.getStatusCode().is2xxSuccessful()).as("explotación: %s", r.getBody()).isTrue();
        return r.getBody().get("id").asLong();
    }

    private String newAudit(long farmId, String type, String leadAuditor) {
        ResponseEntity<JsonNode> r = call(HttpMethod.POST, "/api/audits", Map.of("farmId", farmId,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", type, "leadAuditor", leadAuditor));
        assertThat(r.getStatusCode().is2xxSuccessful()).as("auditoría: %s", r.getBody()).isTrue();
        return r.getBody().get("reference").asText();
    }

    private JsonNode search(String q, int size) {
        // Se envía como URI ya codificada para que los espacios y las tildes no se codifiquen dos veces.
        java.net.URI uri = UriComponentsBuilder.fromUriString(rest.getRootUri() + "/api/audits")
                .queryParam("size", size).queryParam("q", q).encode().build().toUri();
        ResponseEntity<JsonNode> r = rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(null, headers(token)),
                JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private List<String> references(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("content").forEach(a -> out.add(a.get("reference").asText()));
        return out;
    }

    // ---------------------------------------------------------------- búsqueda

    @Test
    void findsAuditsByFarmStandardAuditorTypeAndReferenceAcrossAllPages() {
        String tag = "t" + UUID.randomUUID().toString().substring(0, 6);
        long almeria = newFarm("Cítricos " + tag + " S. Coop.", "Almería");
        long murcia = newFarm("Hortícolas " + tag + " S.L.", "Murcia");
        String first = newAudit(almeria, "INTERNAL", "Ana García");
        String second = newAudit(murcia, "SELF_ASSESSMENT", "Luis Pérez");
        for (int i = 0; i < 4; i++) {
            newAudit(murcia, "SURVEILLANCE", "Otra Persona");
        }

        // Sin tildes ni mayúsculas, por la explotación y su localidad, aunque esté en otra página.
        assertThat(references(search("citricos " + tag, 2))).containsExactly(first);
        assertThat(references(search("ALMERIA " + tag, 2))).containsExactly(first);
        // Por el auditor líder, el tipo y la referencia.
        assertThat(references(search("garcia " + tag, 5))).containsExactly(first);
        assertThat(references(search("autoevaluacion " + tag, 5))).containsExactly(second);
        assertThat(references(search(second, 5))).containsExactly(second);
        // Por el estándar y el estado; todas las palabras deben coincidir.
        assertThat(search("ifa borrador " + tag, 100).get("totalElements").asInt()).isEqualTo(6);
        assertThat(search("cerrada " + tag, 100).get("totalElements").asInt()).isZero();
        // Los resultados se paginan.
        JsonNode page = search("seguimiento " + tag, 3);
        assertThat(page.get("totalElements").asInt()).isEqualTo(4);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void anEmptySearchListsEverything() {
        long farm = newFarm("Finca " + UUID.randomUUID().toString().substring(0, 6), "Lorca");
        newAudit(farm, "INTERNAL", "Ana García");
        int all = call(HttpMethod.GET, "/api/audits?size=100", null).getBody().get("totalElements").asInt();
        assertThat(search("   ", 100).get("totalElements").asInt()).isEqualTo(all);
    }
}
