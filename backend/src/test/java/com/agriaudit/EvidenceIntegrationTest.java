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

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Evidencias reutilizables entre normas (O4, RF-401 a RF-406): registros por explotación, matriz de equivalencias,
 * sugerencias («cubierto por…») en el checklist, vínculos confirmados, propagación y salud del sistema de gestión.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class EvidenceIntegrationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-evidence");
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
        JsonNode body = r.getBody();
        return (body.has("record") ? body.get("record") : body).get("id").asLong();
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String token(String role) {
        String email = role.toLowerCase() + "." + unique() + "@demo.test";
        id(call(admin, HttpMethod.POST, "/api/users",
                Map.of("email", email, "fullName", role + " " + unique(), "role", role, "password", "Abcd1234!")));
        // Los usuarios que crea un administrador entran con una contraseña temporal y deben cambiarla.
        return FirstLogin.withTemporaryPassword(rest, email, "Abcd1234!");
    }

    private long farm() {
        return id(call(admin, HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique(), "country", "España")));
    }

    private long audit(long farm, String standard) {
        return id(call(admin, HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", standard,
                "auditType", "INTERNAL", "leadAuditor", "Auditor " + unique())));
    }

    private Map<String, Object> record(long farm, String type, int issuedDaysAgo, Integer validDays, String outcome) {
        LocalDate issued = LocalDate.now().minusDays(issuedDaysAgo);
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("farmId", farm);
        m.put("typeCode", type);
        m.put("title", type + " " + unique());
        m.put("issuedOn", issued.toString());
        if (validDays != null) {
            m.put("validUntil", issued.plusDays(validDays).toString());
        }
        m.put("outcome", outcome);
        return m;
    }

    private long controlPointId(long audit, String ref) {
        for (JsonNode r : call(admin, HttpMethod.GET, "/api/audits/" + audit + "/results", null).getBody()) {
            if (ref.equals(r.get("ref").asText())) {
                return r.get("controlPointId").asLong();
            }
        }
        throw new AssertionError("No hay punto " + ref);
    }

    private JsonNode item(JsonNode coverage, String ref) {
        for (JsonNode i : coverage.get("items")) {
            if (ref.equals(i.get("controlRef").asText())) {
                return i;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- pruebas

    @Test
    void theMatrixStartsAsADraftThatEachOrganizationCanValidateAndCorrect() {
        JsonNode matrix = call(admin, HttpMethod.GET, "/api/evidence/matrix", null).getBody();
        assertThat(matrix.get("types").size()).isGreaterThanOrEqualTo(10);
        assertThat(matrix.get("entries").size()).isGreaterThan(20);
        for (JsonNode e : matrix.get("entries")) {
            assertThat(e.get("status").asText()).isEqualTo("DRAFT");
        }
        // «Análisis de agua» sirve para varias normas a la vez: eso es la reutilización.
        List<String> standards = new java.util.ArrayList<>();
        for (JsonNode e : matrix.get("entries")) {
            if ("WATER_ANALYSIS".equals(e.get("typeCode").asText())) {
                standards.add(e.get("standardCode").asText());
            }
        }
        assertThat(standards).contains("GLOBALGAP_IFA_V6", "SMETA_6_1");

        long entry = matrix.get("entries").get(0).get("id").asLong();
        ResponseEntity<JsonNode> validated = call(admin, HttpMethod.PUT, "/api/evidence/matrix/" + entry + "/validation",
                Map.of("validated", true));
        assertThat(validated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validated.getBody().get("status").asText()).isEqualTo("VALIDATED");

        // Añadir una equivalencia propia (ya validada) y quitarla; datos inválidos se rechazan.
        ResponseEntity<JsonNode> added = call(admin, HttpMethod.POST, "/api/evidence/matrix",
                Map.of("typeCode", "FIRST_AID", "standardCode", "GLOBALGAP_IFA_V6", "controlRef", "AF 11.5"));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(added.getBody().get("status").asText()).isEqualTo("VALIDATED");
        assertThat(call(admin, HttpMethod.POST, "/api/evidence/matrix",
                Map.of("typeCode", "FIRST_AID", "standardCode", "GLOBALGAP_IFA_V6", "controlRef", "AF 11.5")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(admin, HttpMethod.POST, "/api/evidence/matrix",
                Map.of("typeCode", "FIRST_AID", "standardCode", "GLOBALGAP_IFA_V6", "controlRef", "NO EXISTE")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(admin, HttpMethod.DELETE, "/api/evidence/matrix/" + added.getBody().get("id").asLong(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Solo administración y calidad editan la matriz.
        String auditor = token("AUDITOR");
        assertThat(call(auditor, HttpMethod.POST, "/api/evidence/matrix",
                Map.of("typeCode", "FIRST_AID", "standardCode", "GLOBALGAP_IFA_V6", "controlRef", "AF 11.5")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(auditor, HttpMethod.GET, "/api/evidence/matrix", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void recordsAreRegisteredOnceWithValidationRolesAndADocument() {
        long farm = farm();
        Map<String, Object> body = record(farm, "WATER_ANALYSIS", 10, 365, "FAVORABLE");
        ResponseEntity<JsonNode> created = call(admin, HttpMethod.POST, "/api/evidence/records", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long record = id(created);
        assertThat(created.getBody().get("record").get("status").asText()).isEqualTo("CURRENT");
        assertThat(created.getBody().get("affectedAudits").asInt()).isZero();

        // Validaciones
        Map<String, Object> bad = new java.util.HashMap<>(body);
        bad.put("validUntil", LocalDate.now().minusDays(100).toString());
        assertThat(call(admin, HttpMethod.POST, "/api/evidence/records", bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        bad = new java.util.HashMap<>(body);
        bad.put("typeCode", "NO_EXISTE");
        assertThat(call(admin, HttpMethod.POST, "/api/evidence/records", bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        bad = new java.util.HashMap<>(body);
        bad.put("farmId", 9_999_999L);
        assertThat(call(admin, HttpMethod.POST, "/api/evidence/records", bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Roles: calidad y auditor registran; el lector solo consulta.
        assertThat(call(token("QUALITY"), HttpMethod.POST, "/api/evidence/records", record(farm, "RISK_ASSESSMENT", 5, 365, null))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call(token("AUDITOR"), HttpMethod.POST, "/api/evidence/records", record(farm, "FIRST_AID", 5, 365, null))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String viewer = token("VIEWER");
        assertThat(call(viewer, HttpMethod.POST, "/api/evidence/records", record(farm, "FIRST_AID", 5, 365, null)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.GET, "/api/evidence/records?farmId=" + farm, null).getBody().size()).isEqualTo(3);

        // Un documento una sola vez
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(PNG) {
            @Override
            public String getFilename() {
                return "boletin.png";
            }
        });
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(admin);
        ResponseEntity<JsonNode> up = rest.exchange("/api/evidence/records/" + record + "/attachments", HttpMethod.POST,
                new HttpEntity<>(parts, h), JsonNode.class);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode detail = call(admin, HttpMethod.GET, "/api/evidence/records/" + record, null).getBody();
        assertThat(detail.get("attachments").size()).isEqualTo(1);
        assertThat(detail.get("record").get("documents").asInt()).isEqualTo(1);

        // Un registro de otra explotación o inexistente no se ve
        assertThat(call(admin, HttpMethod.GET, "/api/evidence/records/9999999", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Se registra en la traza
        assertThat(call(admin, HttpMethod.GET, "/api/trace?entityType=EVIDENCE_RECORD&size=50", null).getBody().get("content")
                .toString()).contains("Registró la evidencia");
    }

    @Test
    void oneRecordCoversPointsOfSeveralNormsAndTheLinkMustBeConfirmed() {
        long farm = farm();
        long ifa = audit(farm, "GLOBALGAP_IFA_V6");
        long smeta = audit(farm, "SMETA_6_1");
        long water = id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "WATER_ANALYSIS", 20, 365, "FAVORABLE")));

        // Sugerencia en las dos normas, sin vínculo todavía.
        JsonNode ifaCoverage = call(admin, HttpMethod.GET, "/api/audits/" + ifa + "/evidence-coverage", null).getBody();
        JsonNode fv93 = item(ifaCoverage, "FV 9.3");
        assertThat(fv93).isNotNull();
        assertThat(fv93.get("covered").asBoolean()).isFalse();
        JsonNode suggestion = fv93.get("types").get(0).get("records").get(0);
        assertThat(suggestion.get("id").asLong()).isEqualTo(water);
        assertThat(suggestion.get("linkable").asBoolean()).isTrue();
        assertThat(suggestion.get("linkId").isNull()).isTrue();
        JsonNode smetaCoverage = call(admin, HttpMethod.GET, "/api/audits/" + smeta + "/evidence-coverage", null).getBody();
        assertThat(item(smetaCoverage, "3.D")).isNotNull();

        // Confirmar el vínculo en las dos auditorías (mismo registro, dos normas)
        long cpIfa = controlPointId(ifa, "FV 9.3");
        long cpSmeta = controlPointId(smeta, "3.D");
        ResponseEntity<JsonNode> linked = call(admin, HttpMethod.POST, "/api/audits/" + ifa + "/evidence-links",
                Map.of("controlPointId", cpIfa, "recordId", water));
        assertThat(linked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(item(linked.getBody(), "FV 9.3").get("covered").asBoolean()).isTrue();
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + smeta + "/evidence-links",
                Map.of("controlPointId", cpSmeta, "recordId", water)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + ifa + "/evidence-links",
                Map.of("controlPointId", cpIfa, "recordId", water)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // No sirve para un punto que la matriz no relaciona con ese tipo.
        long cpOther = controlPointId(ifa, "AF 2.1");
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + ifa + "/evidence-links",
                Map.of("controlPointId", cpOther, "recordId", water)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Ni de otra explotación
        long otherFarm = farm();
        long otherWater = id(call(admin, HttpMethod.POST, "/api/evidence/records", record(otherFarm, "WATER_ANALYSIS", 5, 365, null)));
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + ifa + "/evidence-links",
                Map.of("controlPointId", cpIfa, "recordId", otherWater)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Propagación: el registro dice dónde se usa y cambiarlo avisa de cuántas auditorías afecta.
        JsonNode detail = call(admin, HttpMethod.GET, "/api/evidence/records/" + water, null).getBody();
        assertThat(detail.get("uses").size()).isEqualTo(2);
        assertThat(detail.get("record").get("usedIn").asInt()).isEqualTo(2);
        Map<String, Object> change = record(farm, "WATER_ANALYSIS", 20, 40, "FAVORABLE");
        change.put("title", detail.get("record").get("title").asText());
        ResponseEntity<JsonNode> updated = call(admin, HttpMethod.PUT, "/api/evidence/records/" + water, change);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("affectedAudits").asInt()).isEqualTo(2);
        assertThat(updated.getBody().get("record").get("status").asText()).isEqualTo("DUE_SOON");
        // …y se refleja en ambas auditorías sin tocarlas.
        JsonNode after = call(admin, HttpMethod.GET, "/api/audits/" + ifa + "/evidence-coverage", null).getBody();
        assertThat(item(after, "FV 9.3").get("types").get(0).get("records").get(0).get("status").asText()).isEqualTo("DUE_SOON");

        // No se puede borrar ni cambiar de explotación mientras se use.
        assertThat(call(admin, HttpMethod.DELETE, "/api/evidence/records/" + water, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> moved = new java.util.HashMap<>(change);
        moved.put("farmId", otherFarm);
        assertThat(call(admin, HttpMethod.PUT, "/api/evidence/records/" + water, moved).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Quitar el vínculo y entonces sí se puede borrar
        JsonNode linkInfo = item(after, "FV 9.3").get("types").get(0).get("records").get(0);
        long linkId = linkInfo.get("linkId").asLong();
        assertThat(call(admin, HttpMethod.DELETE, "/api/audits/" + ifa + "/evidence-links/" + linkId, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(admin, HttpMethod.GET, "/api/evidence/records/" + water, null).getBody().get("uses").size()).isEqualTo(1);

        // El auditor ve el vínculo en la traza de su auditoría
        assertThat(call(admin, HttpMethod.GET, "/api/audits/" + ifa + "/trace?size=50", null).getBody().toString())
                .contains("Vinculó la evidencia").contains("Quitó el vínculo");
    }

    @Test
    void anExpiredOrUnfavourableRecordCannotCoverAPointAndTheViewerCannotLink() {
        long farm = farm();
        long audit = audit(farm, "GLOBALGAP_IFA_V6");
        long cp = controlPointId(audit, "FV 9.3");
        long expired = id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "WATER_ANALYSIS", 400, 365, "FAVORABLE")));
        long bad = id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "WATER_ANALYSIS", 5, 365, "UNFAVORABLE")));

        JsonNode coverage = call(admin, HttpMethod.GET, "/api/audits/" + audit + "/evidence-coverage", null).getBody();
        JsonNode records = item(coverage, "FV 9.3").get("types").get(0).get("records");
        assertThat(records.size()).isEqualTo(2);
        for (JsonNode r : records) {
            assertThat(r.get("linkable").asBoolean()).isFalse();
            assertThat(r.get("notLinkableReason").asText()).isNotBlank();
        }
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/evidence-links",
                Map.of("controlPointId", cp, "recordId", expired)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/evidence-links",
                Map.of("controlPointId", cp, "recordId", bad)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        long good = id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "WATER_ANALYSIS", 3, 365, "FAVORABLE")));
        assertThat(call(token("VIEWER"), HttpMethod.POST, "/api/audits/" + audit + "/evidence-links",
                Map.of("controlPointId", cp, "recordId", good)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(token("QUALITY"), HttpMethod.POST, "/api/audits/" + audit + "/evidence-links",
                Map.of("controlPointId", cp, "recordId", good)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/evidence-links",
                Map.of("controlPointId", cp, "recordId", good)).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Si después el registro se marca desfavorable, el punto queda con alerta en la auditoría.
        Map<String, Object> change = record(farm, "WATER_ANALYSIS", 3, 365, "UNFAVORABLE");
        assertThat(call(admin, HttpMethod.PUT, "/api/evidence/records/" + good, change).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode alert = item(call(admin, HttpMethod.GET, "/api/audits/" + audit + "/evidence-coverage", null).getBody(), "FV 9.3");
        assertThat(alert.get("alert").asBoolean()).isTrue();
    }

    @Test
    void theHealthPanelShowsEachTypeOfEveryFarmWithItsState() {
        long farm = farm();
        id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "WATER_ANALYSIS", 10, 365, "FAVORABLE")));
        id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "RISK_ASSESSMENT", 100, 120, null)));
        id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "EQUIPMENT_INSPECTION", 400, 365, null)));
        id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "SOIL_ANALYSIS", 5, 365, "UNFAVORABLE")));

        JsonNode health = call(admin, HttpMethod.GET, "/api/evidence/health", null).getBody();
        assertThat(health.get("renewalWindowDays").asInt()).isEqualTo(30);
        JsonNode row = null;
        for (JsonNode f : health.get("farms")) {
            if (f.get("farmId").asLong() == farm) {
                row = f;
            }
        }
        assertThat(row).isNotNull();
        JsonNode cells = row.get("cells");
        assertThat(cells.get("WATER_ANALYSIS").get("status").asText()).isEqualTo("CURRENT");
        assertThat(cells.get("RISK_ASSESSMENT").get("status").asText()).isEqualTo("DUE_SOON");
        assertThat(cells.get("EQUIPMENT_INSPECTION").get("status").asText()).isEqualTo("EXPIRED");
        assertThat(cells.get("SOIL_ANALYSIS").get("status").asText()).isEqualTo("UNFAVORABLE");
        assertThat(cells.get("FIRST_AID").get("status").asText()).isEqualTo("MISSING");
        assertThat(health.get("summary").get("MISSING").asInt()).isGreaterThan(0);
    }

    @Test
    void evidenceDataTravelsInTheOrganizationExportAndThePersonExport() throws Exception {
        long farm = farm();
        id(call(admin, HttpMethod.POST, "/api/evidence/records", record(farm, "WATER_ANALYSIS", 10, 365, "FAVORABLE")));
        Long adminId = jdbc.queryForObject("SELECT id FROM identity.app_users WHERE email = 'admin@demo.test'", Long.class);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(admin);
        byte[] org = rest.exchange("/api/organization/export", HttpMethod.GET, new HttpEntity<>(h), byte[].class).getBody();
        assertThat(zipNames(org)).contains("datos/evidencias-registros.json", "datos/evidencias-vinculos.json",
                "datos/evidencias-matriz.json");
        byte[] person = rest.exchange("/api/users/" + adminId + "/data-export", HttpMethod.GET, new HttpEntity<>(h), byte[].class)
                .getBody();
        assertThat(zipNames(person)).contains("datos/evidencias-registradas.json");
    }

    private List<String> zipNames(byte[] zip) throws java.io.IOException {
        List<String> names = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (java.util.zip.ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        return names;
    }
}
