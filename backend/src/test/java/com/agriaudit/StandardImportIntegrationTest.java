package com.agriaudit;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listados propios por organización (RF-CAT-*): plantilla, validación, importación, uso en una auditoría, versiones,
 * privacidad entre organizaciones y visibilidad de los estándares de demostración.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class StandardImportIntegrationTest {

    private static final String CSV = "Capítulo;Título del capítulo;Referencia;Título;Descripción;Nivel\n"
            + "1;Gestión;1.1;Requisito de registro;Texto propio;Obligación mayor\n"
            + "1;Gestión;1.2;Requisito de formación;;Obligación menor\n"
            + "2;Higiene;2.1;Recomendación de higiene;;Recomendación\n";

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-import");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.demo.seed", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

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

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String userWithRole(String role) {
        String email = role.toLowerCase() + "." + unique() + "@demo.test";
        call(admin, HttpMethod.POST, "/api/users", Map.of("email", email, "fullName", role, "role", role,
                "password", "Abcd1234!"));
        return login(email, "Abcd1234!");
    }

    private ResponseEntity<JsonNode> upload(String token, String path, String code, String version, String csv) {
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "listado.csv";
            }
        });
        parts.add("code", code);
        parts.add("name", "Listado de " + code);
        parts.add("version", version);
        parts.add("category", "GAP");
        parts.add("scoringType", "MAJOR_MINOR");
        parts.add("majorMustMinPct", "100");
        parts.add("minorMustMinPct", "95");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(parts, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> preview(String token, String code, String version, String csv) {
        return upload(token, "/api/standards/import/preview", code, version, csv);
    }

    private ResponseEntity<JsonNode> doImport(String token, String code, String version, String csv) {
        return upload(token, "/api/standards/import", code, version, csv);
    }

    private long newFarm(String token) {
        return call(token, HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique(), "country", "España"))
                .getBody().get("id").asLong();
    }

    private boolean listed(String token, String path, String code) {
        for (JsonNode s : call(token, HttpMethod.GET, path, null).getBody()) {
            if (s.get("code").asText().equals(code)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- plantilla, permisos y validación

    @Test
    void theTemplateIsADownloadableExcelAndOnlyAdminAndQualityMayImport() {
        ResponseEntity<byte[]> template = rest.exchange("/api/standards/import/template", HttpMethod.GET,
                new HttpEntity<>(null, json(admin)), byte[].class);
        assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(template.getBody()).startsWith((byte) 'P', (byte) 'K');

        String auditor = userWithRole("AUDITOR");
        String viewer = userWithRole("VIEWER");
        String quality = userWithRole("QUALITY");
        assertThat(preview(auditor, "X" + unique(), "1", CSV).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(preview(viewer, "X" + unique(), "1", CSV).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(preview(quality, "Q" + unique(), "1", CSV).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void previewReportsEveryProblemWithRowAndColumnAndSavesNothing() {
        String code = "PRE" + unique();
        String bad = "Capítulo;Referencia;Título;Nivel\n1;1.1;Bien;Obligación mayor\n1;1.1;Repetida;Obligación mayor\n"
                + "1;1.2;Nivel malo;Rarísimo\n";

        JsonNode body = preview(admin, code, "1", bad).getBody();

        assertThat(body.get("valid").asBoolean()).isFalse();
        assertThat(body.get("totalIssues").asInt()).isEqualTo(2);
        assertThat(body.get("issues").get(0).get("row").asInt()).isEqualTo(3);
        assertThat(body.get("issues").get(0).get("column").asText()).isEqualTo("Referencia");
        assertThat(listed(admin, "/api/standards?all=true", code + "_V1")).isFalse();

        // Y una importación con problemas se rechaza.
        assertThat(doImport(admin, code, "1", bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aValidFileGivesAPreviewWithCountsAndASample() {
        JsonNode body = preview(admin, "OK" + unique(), "2", CSV).getBody();

        assertThat(body.get("valid").asBoolean()).isTrue();
        assertThat(body.get("pointCount").asInt()).isEqualTo(3);
        assertThat(body.get("chapterCount").asInt()).isEqualTo(2);
        assertThat(body.get("levelCounts").get("MAJOR_MUST").asInt()).isEqualTo(1);
        assertThat(body.get("sample")).hasSize(3);
        assertThat(body.get("storedCode").asText()).endsWith("_V2");
    }

    @Test
    void aCodeUsedByADemoStandardIsRejected() {
        // El catálogo común trae GLOBALGAP_IFA_V6.
        JsonNode body = preview(admin, "GLOBALGAP_IFA", "6", CSV).getBody();
        assertThat(body.get("valid").asBoolean()).isFalse();
        assertThat(body.get("issues").toString()).contains("demostración");
    }

    // ---------------------------------------------------------------- importar y usar

    @Test
    void anImportedStandardIsUsedToAuditAndScore() {
        String code = "COOP" + unique();
        ResponseEntity<JsonNode> imported = doImport(admin, code, "1.0", CSV);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String stored = imported.getBody().get("code").asText();
        assertThat(imported.getBody().get("custom").asBoolean()).isTrue();
        assertThat(imported.getBody().get("controlPointCount").asInt()).isEqualTo(3);

        // Aparece en el selector y su detalle trae capítulos y puntos.
        assertThat(listed(admin, "/api/standards", stored)).isTrue();
        JsonNode detail = call(admin, HttpMethod.GET, "/api/standards/" + stored, null).getBody();
        assertThat(detail.get("chapters")).hasSize(2);
        assertThat(detail.get("scoringRule").asText()).contains("MAJOR_MINOR");

        // Se crea una auditoría con él y su checklist son sus puntos.
        long farm = newFarm(admin);
        ResponseEntity<JsonNode> audit = call(admin, HttpMethod.POST, "/api/audits", Map.of("farmId", farm,
                "standardCode", stored, "auditType", "INTERNAL", "leadAuditor", "Auditora"));
        assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long auditId = audit.getBody().get("id").asLong();
        JsonNode results = call(admin, HttpMethod.GET, "/api/audits/" + auditId + "/results", null).getBody();
        assertThat(results).hasSize(3);
        List<Map<String, Object>> all = new ArrayList<>();
        results.forEach(r -> all.add(Map.of("controlPointId", r.get("controlPointId").asLong(),
                "compliance", "COMPLIANT")));
        call(admin, HttpMethod.PUT, "/api/audits/" + auditId + "/results", Map.of("results", all));
        JsonNode score = call(admin, HttpMethod.GET, "/api/audits/" + auditId + "/score", null).getBody();
        assertThat(score.get("overallResult").asText()).isEqualTo("PASS");

        // Los documentos genéricos funcionan con un estándar propio.
        ResponseEntity<JsonNode> doc = call(admin, HttpMethod.POST, "/api/audits/" + auditId
                + "/documents/generate", Map.of("documentType", "AUDIT_REPORT", "format", "PDF"));
        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // La importación deja constancia en la traza.
        JsonNode trace = call(admin, HttpMethod.GET, "/api/trace?entityType=STANDARD&size=50", null).getBody()
                .get("content");
        assertThat(trace.toString()).contains("Importó el estándar");
    }

    @Test
    void aNewVersionIsANewStandardAndOldAuditsKeepTheirs() {
        String code = "VER" + unique();
        String v1 = doImport(admin, code, "1", CSV).getBody().get("code").asText();
        long farm = newFarm(admin);
        long oldAudit = call(admin, HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", v1,
                "auditType", "INTERNAL", "leadAuditor", "A")).getBody().get("id").asLong();

        // Repetir la misma versión no se admite; una versión nueva sí, con otros puntos.
        assertThat(doImport(admin, code, "1", CSV).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String csv2 = CSV + "3;Nuevo;3.1;Requisito añadido en la versión nueva;;Obligación mayor\n";
        ResponseEntity<JsonNode> second = doImport(admin, code, "2", csv2);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String v2 = second.getBody().get("code").asText();
        assertThat(v2).isNotEqualTo(v1);
        assertThat(second.getBody().get("controlPointCount").asInt()).isEqualTo(4);

        // La auditoría antigua sigue con sus 3 puntos.
        assertThat(call(admin, HttpMethod.GET, "/api/audits/" + oldAudit + "/results", null).getBody()).hasSize(3);

        // Archivar la versión antigua la quita del selector, pero no de lo ya auditado.
        assertThat(call(admin, HttpMethod.PUT, "/api/standards/" + v1 + "/archived?value=true", null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(listed(admin, "/api/standards", v1)).isFalse();
        assertThat(listed(admin, "/api/standards?all=true", v1)).isTrue();
        assertThat(call(admin, HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", v1,
                "auditType", "INTERNAL", "leadAuditor", "A")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(admin, HttpMethod.GET, "/api/audits/" + oldAudit + "/results", null).getBody()).hasSize(3);
    }

    // ---------------------------------------------------------------- privacidad y visibilidad

    @Test
    void anImportedStandardIsPrivateToItsOrganization() {
        String stored = doImport(admin, "PRIV" + unique(), "1", CSV).getBody().get("code").asText();

        Long otherOrg = jdbc.queryForObject("INSERT INTO identity.organizations (name) VALUES ('Cooperativa vecina') "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id", Long.class);
        jdbc.update("INSERT INTO identity.app_users (organization_id, email, password_hash, full_name, role) "
                        + "VALUES (?, 'vecina@demo.test', ?, 'Vecina', 'ADMIN') ON CONFLICT (email) DO NOTHING",
                otherOrg, passwordEncoder.encode("Vecina123!"));
        String neighbour = login("vecina@demo.test", "Vecina123!");

        assertThat(listed(neighbour, "/api/standards?all=true", stored)).isFalse();
        assertThat(call(neighbour, HttpMethod.GET, "/api/standards/" + stored, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        long farm = newFarm(neighbour);
        assertThat(call(neighbour, HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", stored,
                "auditType", "INTERNAL", "leadAuditor", "A")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Y la vecina puede usar el mismo código sin chocar con el nuestro.
        String code = stored.substring(0, stored.lastIndexOf("_V"));
        assertThat(doImport(neighbour, code, "1", CSV).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void anOrganizationCanHideTheDemoStandardsWithoutAffectingOthers() {
        String demo = "GRASP_V2";
        assertThat(listed(admin, "/api/standards", demo)).isTrue();

        assertThat(call(admin, HttpMethod.PUT, "/api/standards/" + demo + "/hidden?value=true", null)
                .getStatusCode().is2xxSuccessful()).isTrue();
        try {
            assertThat(listed(admin, "/api/standards", demo)).isFalse();
            JsonNode all = call(admin, HttpMethod.GET, "/api/standards?all=true", null).getBody();
            boolean flagged = false;
            for (JsonNode s : all) {
                if (s.get("code").asText().equals(demo)) {
                    flagged = s.get("hidden").asBoolean();
                }
            }
            assertThat(flagged).isTrue();
            // No se pueden crear auditorías nuevas con un estándar oculto.
            assertThat(call(admin, HttpMethod.POST, "/api/audits", Map.of("farmId", newFarm(admin),
                    "standardCode", demo, "auditType", "INTERNAL", "leadAuditor", "A")).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            // Un estándar propio no se oculta: se archiva.
            String own = doImport(admin, "OWN" + unique(), "1", CSV).getBody().get("code").asText();
            assertThat(call(admin, HttpMethod.PUT, "/api/standards/" + own + "/hidden?value=true", null)
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(call(admin, HttpMethod.PUT, "/api/standards/" + demo + "/archived?value=true", null)
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            call(admin, HttpMethod.PUT, "/api/standards/" + demo + "/hidden?value=false", null);
        }
        assertThat(listed(admin, "/api/standards", demo)).isTrue();
    }
}
