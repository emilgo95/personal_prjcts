package com.agriaudit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.agriaudit.support.TestPostgres;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RT-27: las referencias de negocio (AUD-…, NC-…) no se repiten ni con altas simultáneas ni al
 * borrar registros.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ReferenceConcurrencyIntegrationTest {

    private static final int PARALLEL = 12;

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-refs");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
    }

    @Autowired
    TestRestTemplate rest;

    private HttpHeaders headers;

    @BeforeEach
    void login() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", "admin@demo.test", "password", "Admin123!"), h), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        h.setBearerAuth(login.getBody().get("accessToken").asText());
        headers = h;
    }

    @Test
    void concurrentFindingCreationNeverSharesAReference() throws Exception {
        long auditId = createAudit(createFarm());

        List<ResponseEntity<JsonNode>> responses = runConcurrently(() -> rest.postForEntity(
                "/api/audits/" + auditId + "/findings",
                new HttpEntity<>(Map.of("classification", "MINOR", "title", "NC concurrente"), headers),
                JsonNode.class));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED));
        List<String> references = responses.stream().map(r -> r.getBody().get("reference").asText()).toList();
        assertThat(references).doesNotHaveDuplicates().allMatch(ref -> ref.matches("NC-\\d{3}"));
    }

    @Test
    void deletingAFindingDoesNotReuseAnExistingReference() {
        long auditId = createAudit(createFarm());
        long first = createFinding(auditId).get("id").asLong();
        String second = createFinding(auditId).get("reference").asText();

        ResponseEntity<Void> deleted = rest.exchange("/api/audits/" + auditId + "/findings/" + first,
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String third = createFinding(auditId).get("reference").asText();
        assertThat(third).isNotEqualTo(second);
    }

    @Test
    void concurrentAuditCreationNeverSharesAReference() throws Exception {
        long farmId = createFarm();

        List<ResponseEntity<JsonNode>> responses = runConcurrently(() -> rest.postForEntity("/api/audits",
                new HttpEntity<>(Map.of("farmId", farmId, "standardCode", "GLOBALGAP_IFA_V6",
                        "auditType", "SELF_ASSESSMENT"), headers),
                JsonNode.class));

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED));
        assertThat(responses.stream().map(r -> r.getBody().get("reference").asText()).toList())
                .doesNotHaveDuplicates();
    }

    // ---- helpers ----

    private <T> List<T> runConcurrently(Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < PARALLEL; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private long createFarm() {
        ResponseEntity<JsonNode> farm = rest.postForEntity("/api/farms",
                new HttpEntity<>(Map.of("legalName", "Finca Concurrencia S.L.", "country", "España"), headers),
                JsonNode.class);
        assertThat(farm.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return farm.getBody().get("id").asLong();
    }

    private long createAudit(long farmId) {
        ResponseEntity<JsonNode> audit = rest.postForEntity("/api/audits",
                new HttpEntity<>(Map.of("farmId", farmId, "standardCode", "GLOBALGAP_IFA_V6",
                        "auditType", "SELF_ASSESSMENT"), headers),
                JsonNode.class);
        assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return audit.getBody().get("id").asLong();
    }

    private JsonNode createFinding(long auditId) {
        ResponseEntity<JsonNode> finding = rest.postForEntity("/api/audits/" + auditId + "/findings",
                new HttpEntity<>(Map.of("classification", "MINOR", "title", "NC"), headers), JsonNode.class);
        assertThat(finding.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return finding.getBody();
    }
}
