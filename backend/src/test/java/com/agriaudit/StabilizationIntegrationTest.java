package com.agriaudit;

import com.agriaudit.support.TestPostgres;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Errores corregidos en la tanda de estabilización: administración de usuarios, inicio de sesión de cuentas
 * desactivadas, auditorías congeladas, coherencia de no conformidades, mensajes de error y validaciones.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class StabilizationIntegrationTest {

    private static final String PNG_1X1 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-stab");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.demo.seed", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    private String token;

    @BeforeEach
    void loginAsAdmin() {
        token = login("admin@demo.test", "Admin123!");
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

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, json(token)), JsonNode.class);
    }

    private long id(ResponseEntity<JsonNode> r) {
        assertThat(r.getStatusCode().is2xxSuccessful()).as("respuesta %s: %s", r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody().get("id").asLong();
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long newFarm() {
        return id(call(HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique(), "country", "España")));
    }

    private long newAudit(long farmId) {
        return id(call(HttpMethod.POST, "/api/audits", Map.of("farmId", farmId, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "INTERNAL", "leadAuditor", "Ana Auditora")));
    }

    private List<Long> controlPoints(long auditId) {
        List<Long> out = new ArrayList<>();
        call(HttpMethod.GET, "/api/audits/" + auditId + "/results", null).getBody()
                .forEach(i -> out.add(i.get("controlPointId").asLong()));
        return out;
    }

    private void assessAll(long auditId) {
        List<Map<String, Object>> all = new ArrayList<>();
        controlPoints(auditId).forEach(cp -> all.add(Map.of("controlPointId", cp, "compliance", "COMPLIANT")));
        assertThat(call(HttpMethod.PUT, "/api/audits/" + auditId + "/results", Map.of("results", all))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpStatus transition(long auditId, String status) {
        return HttpStatus.valueOf(call(HttpMethod.POST, "/api/audits/" + auditId + "/transition",
                Map.of("status", status)).getStatusCode().value());
    }

    private ResponseEntity<JsonNode> upload(String path) {
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(Base64.getDecoder().decode(PNG_1X1)) {
            @Override
            public String getFilename() {
                return "evidencia.png";
            }
        });
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(parts, h), JsonNode.class);
    }

    // ---------------------------------------------------------------- B1: administradores

    @Test
    void anAdministratorCannotLockTheOrganizationOut() {
        long myId = call(HttpMethod.GET, "/api/auth/me", null).getBody().get("id").asLong();

        ResponseEntity<JsonNode> demote = call(HttpMethod.PUT, "/api/users/" + myId,
                Map.of("fullName", "Admin", "role", "VIEWER", "active", true));
        assertThat(demote.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> deactivate = call(HttpMethod.PUT, "/api/users/" + myId,
                Map.of("fullName", "Admin", "role", "ADMIN", "active", false));
        assertThat(deactivate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Editar su propio nombre, conservando rol y estado, sí se puede.
        assertThat(call(HttpMethod.PUT, "/api/users/" + myId,
                Map.of("fullName", "Administrador", "role", "ADMIN")).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Otro administrador sí puede ser degradado por este.
        long other = id(call(HttpMethod.POST, "/api/users", Map.of("email", "admin2." + unique() + "@demo.test",
                "fullName", "Segundo", "role", "ADMIN", "password", "Abcd1234!")));
        assertThat(call(HttpMethod.PUT, "/api/users/" + other,
                Map.of("fullName", "Segundo", "role", "VIEWER", "active", true)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- B2: usuario desactivado

    @Test
    void aDeactivatedUserGetsTheSameGenericUnauthorizedAsAWrongPassword() {
        String email = "baja." + unique() + "@demo.test";
        long uid = id(call(HttpMethod.POST, "/api/users",
                Map.of("email", email, "fullName", "Baja", "role", "VIEWER", "password", "Abcd1234!")));
        assertThat(call(HttpMethod.PUT, "/api/users/" + uid,
                Map.of("fullName", "Baja", "role", "VIEWER", "active", false)).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> disabled = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", "Abcd1234!"), json(null)), JsonNode.class);
        ResponseEntity<JsonNode> wrong = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", "otra-clave"), json(null)), JsonNode.class);

        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(disabled.getBody().get("message").asText()).isEqualTo(wrong.getBody().get("message").asText());
    }

    // ---------------------------------------------------------------- B5: auditorías congeladas

    @Test
    void aSubmittedOrClosedAuditCannotBeModified() {
        long farm = newFarm();
        long audit = newAudit(farm);
        long cp = controlPoints(audit).get(0);
        assessAll(audit);

        // Evidencia y NC creadas mientras la auditoría está abierta.
        long finding = id(call(HttpMethod.POST, "/api/audits/" + audit + "/findings",
                Map.of("controlPointId", cp, "classification", "OBSERVATION", "title", "Observación")));
        long attachment = id(upload("/api/audits/" + audit + "/findings/" + finding + "/attachments"));

        assertThat(transition(audit, "CLOSING")).isEqualTo(HttpStatus.OK);
        assertThat(transition(audit, "SUBMITTED")).isEqualTo(HttpStatus.OK);
        assertFrozen(audit, farm, cp, finding, attachment);

        // Reabrir la enviada permite volver a editar...
        assertThat(transition(audit, "CLOSING")).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results",
                List.of(Map.of("controlPointId", cp, "compliance", "COMPLIANT")))).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // ...y una cerrada queda congelada para siempre (la observación, única NC, se cierra antes).
        assertThat(call(HttpMethod.PUT, "/api/audits/" + audit + "/findings/" + finding, Map.of(
                "controlPointId", cp, "classification", "OBSERVATION", "title", "Observación", "status", "CLOSED"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transition(audit, "SUBMITTED")).isEqualTo(HttpStatus.OK);
        assertThat(transition(audit, "CLOSED")).isEqualTo(HttpStatus.OK);
        assertFrozen(audit, farm, cp, finding, attachment);
    }

    private void assertFrozen(long audit, long farm, long cp, long finding, long attachment) {
        String base = "/api/audits/" + audit;
        assertThat(call(HttpMethod.PUT, base + "/results", Map.of("results",
                List.of(Map.of("controlPointId", cp, "compliance", "NON_COMPLIANT")))).getStatusCode())
                .as("editar el checklist").isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.PUT, base, Map.of("farmId", farm, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "INTERNAL", "scopeDescription", "cambiado")).getStatusCode())
                .as("editar los datos de la auditoría").isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.POST, base + "/findings", Map.of("controlPointId", cp, "classification", "MINOR",
                "title", "Tardía")).getStatusCode()).as("crear una NC").isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.PUT, base + "/findings/" + finding, Map.of("classification", "OBSERVATION",
                "title", "Cambiada")).getStatusCode()).as("editar una NC").isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.POST, base + "/findings/" + finding + "/corrective-actions",
                Map.of("rootCause", "x")).getStatusCode()).as("crear una acción").isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.DELETE, base + "/findings/" + finding, null).getStatusCode())
                .as("borrar una NC").isEqualTo(HttpStatus.CONFLICT);
        assertThat(upload(base + "/findings/" + finding + "/attachments").getStatusCode())
                .as("subir evidencia a una NC").isEqualTo(HttpStatus.CONFLICT);
        assertThat(upload(base + "/results/" + cp + "/attachments").getStatusCode())
                .as("subir evidencia a un punto de control").isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.DELETE, "/api/attachments/" + attachment, null).getStatusCode())
                .as("borrar evidencia").isEqualTo(HttpStatus.CONFLICT);
        // Consultar y generar documentos sigue permitido.
        assertThat(call(HttpMethod.GET, base + "/findings", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, base + "/documents/generate",
                Map.of("documentType", "CHECKLIST", "format", "XLSX")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ---------------------------------------------------------------- B6: estados de NC y acciones

    @Test
    void findingStatusStaysCoherentWithItsCorrectiveActions() {
        long audit = newAudit(newFarm());
        long cp = controlPoints(audit).get(1);
        String findings = "/api/audits/" + audit + "/findings";
        long finding = id(call(HttpMethod.POST, findings,
                Map.of("controlPointId", cp, "classification", "MAJOR", "title", "NC", "dueDate", "2026-12-31")));
        Map<String, Object> close = Map.of("controlPointId", cp, "classification", "MAJOR", "title", "NC",
                "status", "CLOSED");

        // Sin acciones no se puede cerrar a mano.
        assertThat(call(HttpMethod.PUT, findings + "/" + finding, close).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Tampoco crear una NC ya cerrada.
        assertThat(call(HttpMethod.POST, findings, close).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Con una acción pendiente de verificar, tampoco.
        long action = id(call(HttpMethod.POST, findings + "/" + finding + "/corrective-actions",
                Map.of("rootCause", "c", "correction", "c", "preventiveAction", "p", "status", "COMPLETED")));
        assertThat(status(audit, finding)).isEqualTo("IN_PROGRESS");
        assertThat(call(HttpMethod.PUT, findings + "/" + finding, close).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Al verificar la única acción, la NC se cierra sola...
        assertThat(call(HttpMethod.PUT, findings + "/" + finding + "/corrective-actions/" + action,
                Map.of("rootCause", "c", "status", "VERIFIED")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(audit, finding)).isEqualTo("CLOSED");

        // ...y si la acción deja de estar verificada, se reabre.
        assertThat(call(HttpMethod.PUT, findings + "/" + finding + "/corrective-actions/" + action,
                Map.of("rootCause", "c", "status", "IN_PROGRESS")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(audit, finding)).isEqualTo("IN_PROGRESS");
        call(HttpMethod.PUT, findings + "/" + finding + "/corrective-actions/" + action,
                Map.of("rootCause", "c", "status", "VERIFIED"));
        assertThat(status(audit, finding)).isEqualTo("CLOSED");

        // Borrar la única acción de una NC cerrada no la deja cerrada sin evidencia.
        assertThat(call(HttpMethod.DELETE, findings + "/" + finding + "/corrective-actions/" + action, null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(audit, finding)).isEqualTo("OPEN");

        // Una observación sí puede cerrarse sin acción correctiva.
        long observation = id(call(HttpMethod.POST, findings,
                Map.of("controlPointId", cp, "classification", "OBSERVATION", "title", "Obs")));
        assertThat(call(HttpMethod.PUT, findings + "/" + observation, Map.of("controlPointId", cp,
                "classification", "OBSERVATION", "title", "Obs", "status", "CLOSED")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private String status(long audit, long finding) {
        for (JsonNode f : call(HttpMethod.GET, "/api/audits/" + audit + "/findings", null).getBody()) {
            if (f.get("id").asLong() == finding) {
                return f.get("status").asText();
            }
        }
        throw new AssertionError("NC no encontrada");
    }

    // ---------------------------------------------------------------- B8: mensajes de error

    @Test
    void errorsAreMappedToTheRightStatusWithoutLeakingInternals() {
        long audit = newAudit(newFarm());
        String findings = "/api/audits/" + audit + "/findings";

        ResponseEntity<JsonNode> badDate = call(HttpMethod.POST, findings,
                Map.of("classification", "MINOR", "dueDate", "no-es-fecha"));
        assertThat(badDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> malformed = rest.exchange(findings, HttpMethod.POST,
                new HttpEntity<>("{esto no es json", json(token)), String.class);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody()).doesNotContain("Exception", "com.fasterxml", "at java.");

        assertThat(call(HttpMethod.GET, "/api/audits/abc", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.DELETE, "/api/audits/" + audit, null).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(call(HttpMethod.GET, "/api/no-existe", null).getStatusCode().is5xxServerError()).isFalse();
    }

    // ---------------------------------------------------------------- B10: validaciones

    @Test
    void inputIsValidated() {
        String ggn = "4099990" + unique();
        long farm = id(call(HttpMethod.POST, "/api/farms",
                Map.of("legalName", "Con GGN " + unique(), "registrationNumber", ggn)));

        // GGN repetido (también con otra capitalización o espacios) al crear...
        assertThat(call(HttpMethod.POST, "/api/farms", Map.of("legalName", "Duplicada",
                "registrationNumber", " " + ggn.toLowerCase() + " ")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // ...y al editar otra explotación para que lo use.
        long other = newFarm();
        assertThat(call(HttpMethod.PUT, "/api/farms/" + other, Map.of("legalName", "Otra",
                "registrationNumber", ggn)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Una explotación puede guardarse con su propio GGN, y varias pueden no tener ninguno.
        assertThat(call(HttpMethod.PUT, "/api/farms/" + farm, Map.of("legalName", "Editada",
                "registrationNumber", ggn)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, "/api/farms", Map.of("legalName", "Sin GGN 1")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(call(HttpMethod.POST, "/api/farms", Map.of("legalName", "Sin GGN 2")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Longitud y formato.
        assertThat(call(HttpMethod.POST, "/api/farms", Map.of("legalName", "x".repeat(251))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/api/farms", Map.of("legalName", "Correo", "contactEmail", "no-es-correo"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Fecha de fin anterior a la de inicio.
        assertThat(call(HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "INTERNAL", "plannedStartDate", "2026-10-10", "plannedEndDate", "2026-10-01"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/api/audits", Map.of("farmId", farm, "standardCode", "GLOBALGAP_IFA_V6",
                "auditType", "INTERNAL", "plannedStartDate", "2026-10-01", "plannedEndDate", "2026-10-01"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
