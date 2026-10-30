package com.agriaudit;

import com.agriaudit.identity.application.BlockedIdentityDestroyer;
import com.agriaudit.support.FirstLogin;
import com.agriaudit.support.TestPostgres;
import com.agriaudit.trace.application.TraceCrypto;
import com.agriaudit.trace.infrastructure.persistence.StoredEntry;
import com.agriaudit.trace.infrastructure.persistence.TraceRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Privacidad (RS-GDPR): bloqueo de los datos identificativos de un usuario, plazo de conservación y destrucción, traza
 * sin nombres y exportación de los datos de la organización.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PrivacyIntegrationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-privacy");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.demo.seed", () -> "false");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    BlockedIdentityDestroyer destroyer;

    @Autowired
    TraceRepository traceRepository;

    @Autowired
    TraceCrypto crypto;

    @Autowired
    TransactionTemplate transactions;

    private String admin;

    @BeforeEach
    void loginAsAdmin() {
        admin = login("admin@demo.test", "Admin123!");
        // El plazo de la organización vuelve al valor general en cada prueba.
        call(admin, HttpMethod.PUT, "/api/organization/privacy", Map.of());
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
        return r.getBody().get("id").asLong();
    }

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Person(long id, String email, String fullName, String token) {
    }

    private Person person(String role, String fullName) {
        String email = role.toLowerCase() + "." + unique() + "@demo.test";
        long uid = id(call(admin, HttpMethod.POST, "/api/users",
                Map.of("email", email, "fullName", fullName, "role", role, "password", "Abcd1234!")));
        return new Person(uid, email, fullName, FirstLogin.withTemporaryPassword(rest, email, "Abcd1234!"));
    }

    private ResponseEntity<JsonNode> uploadPng(String token, String path) {
        return uploadPng(token, path, false);
    }

    private ResponseEntity<JsonNode> uploadPng(String token, String path, boolean personalData) {
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        if (personalData) {
            parts.add("containsPersonalData", "true");
        }
        parts.add("file", new ByteArrayResource(PNG) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        });
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(parts, h), JsonNode.class);
    }

    private boolean columnContains(String sql, String text) {
        for (String value : jdbc.queryForList(sql, String.class)) {
            if (value != null && value.contains(text)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- bloqueo

    @Test
    void blockingAUserHidesTheirIdentityEverywhereAndKeepsTheOriginalDataReserved() {
        String name = "Ana Bloqueable " + unique();
        Person ana = person("AUDITOR", name);
        Person quality = person("QUALITY", "Calidad Bloqueable " + unique());

        long farm = id(call(ana.token(), HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique())));
        long audit = id(call(ana.token(), HttpMethod.POST, "/api/audits", Map.of("farmId", farm,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", "INTERNAL", "leadAuditor", name)));
        long cp = call(ana.token(), HttpMethod.GET, "/api/audits/" + audit + "/results", null).getBody()
                .get(0).get("controlPointId").asLong();
        call(ana.token(), HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results",
                List.of(Map.of("controlPointId", cp, "compliance", "NON_COMPLIANT"))));
        long finding = id(call(ana.token(), HttpMethod.POST, "/api/audits/" + audit + "/findings",
                Map.of("controlPointId", cp, "classification", "MINOR", "title", "NC de prueba")));
        long action = id(call(ana.token(), HttpMethod.POST, "/api/audits/" + audit + "/findings/" + finding
                + "/corrective-actions", Map.of("rootCause", "Causa")));
        assertThat(uploadPng(ana.token(), "/api/audits/" + audit + "/attachments").getStatusCode()
                .is2xxSuccessful()).isTrue();
        assertThat(call(ana.token(), HttpMethod.POST, "/api/audits/" + audit + "/documents/generate",
                Map.of("documentType", "AUDIT_REPORT", "format", "PDF")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call(quality.token(), HttpMethod.POST, "/api/audits/" + audit + "/findings/" + finding
                + "/corrective-actions/" + action + "/verify", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Antes de bloquear, el nombre y el correo están en esos campos.
        assertThat(columnContains("SELECT assessed_by FROM audit.audit_item_results", ana.email())).isTrue();
        assertThat(columnContains("SELECT uploaded_by FROM storage.attachments", ana.email())).isTrue();
        assertThat(columnContains("SELECT generated_by FROM documents.generated_documents", ana.email())).isTrue();
        assertThat(columnContains("SELECT verified_by FROM findings.corrective_actions", quality.fullName())).isTrue();

        ResponseEntity<JsonNode> blocked = call(admin, HttpMethod.POST, "/api/users/" + ana.id() + "/block", null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blocked.getBody().get("blocked").asBoolean()).isTrue();
        assertThat(blocked.getBody().get("fullName").asText()).isEqualTo("Usuario bloqueado");
        // Su correo no se muestra (ni el real ni el marcador interno que lo sustituye).
        assertThat(blocked.getBody().get("email").isNull()).isTrue();
        assertThat(call(admin, HttpMethod.POST, "/api/users/" + quality.id() + "/block", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Ya no aparecen ni su nombre ni su correo en ningún campo de texto de los módulos.
        for (String text : List.of(ana.email(), ana.fullName(), quality.email(), quality.fullName())) {
            assertThat(columnContains("SELECT assessed_by FROM audit.audit_item_results", text)).as(text).isFalse();
            assertThat(columnContains("SELECT uploaded_by FROM storage.attachments", text)).as(text).isFalse();
            assertThat(columnContains("SELECT generated_by FROM documents.generated_documents", text)).as(text).isFalse();
            assertThat(columnContains("SELECT verified_by FROM findings.corrective_actions", text)).as(text).isFalse();
            assertThat(columnContains("SELECT email FROM identity.app_users", text)).as(text).isFalse();
            assertThat(columnContains("SELECT full_name FROM identity.app_users", text)).as(text).isFalse();
            // La traza inalterable nunca guardó el nombre ni el correo de las entradas nuevas.
            assertThat(columnContains("SELECT coalesce(username,'') || coalesce(user_full_name,'') || summary || "
                    + "changes || coalesce(entity_ref,'') FROM trace.change_log", text)).as(text).isFalse();
        }

        // Los datos originales quedan reservados (sin acceso desde la aplicación) con su fecha de destrucción.
        Map<String, Object> reserved = jdbc.queryForMap("SELECT email, full_name, destroy_after FROM "
                + "identity.blocked_identity WHERE user_id = ?", ana.id());
        assertThat(reserved.get("email")).isEqualTo(ana.email());
        assertThat(reserved.get("full_name")).isEqualTo(ana.fullName());
        Instant destroyAfter = ((java.sql.Timestamp) reserved.get("destroy_after")).toInstant();
        assertThat(destroyAfter).isAfter(Instant.now().plus(58 * 30L, ChronoUnit.DAYS));

        // La cuenta no se puede usar ni modificar, y el historial la muestra oculta.
        assertThat(rest.postForEntity("/api/auth/login", new HttpEntity<>(Map.of("email", ana.email(),
                "password", "Abcd1234!"), json(null)), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(admin, HttpMethod.PUT, "/api/users/" + ana.id(), Map.of("fullName", "Otro", "role", "AUDITOR",
                "active", true)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(admin, HttpMethod.POST, "/api/users/" + ana.id() + "/block", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        JsonNode entries = call(admin, HttpMethod.GET, "/api/audits/" + audit + "/trace?size=100", null).getBody()
                .get("content");
        boolean anyByAna = false;
        for (JsonNode e : entries) {
            if (!e.get("userId").isNull() && e.get("userId").asLong() == ana.id()) {
                anyByAna = true;
                assertThat(e.get("userFullName").asText()).isEqualTo("Usuario bloqueado");
                assertThat(e.get("username").asText()).isEqualTo("usuario-" + ana.id());
            }
        }
        assertThat(anyByAna).isTrue();
        // Y la cadena sigue siendo verificable.
        assertThat(call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody().get("ok").asBoolean()).isTrue();
    }

    @Test
    void onlyAdministrationCanBlockAndNobodyCanBlockThemselves() {
        Person auditor = person("AUDITOR", "Auditor " + unique());
        Person victim = person("VIEWER", "Lector " + unique());
        assertThat(call(auditor.token(), HttpMethod.POST, "/api/users/" + victim.id() + "/block", null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        long adminId = call(admin, HttpMethod.GET, "/api/auth/me", null).getBody().get("id").asLong();
        assertThat(call(admin, HttpMethod.POST, "/api/users/" + adminId + "/block", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---------------------------------------------------------------- plazo de conservación y destrucción

    @Test
    void theRetentionPeriodIsConfigurablePerOrganizationAndValidated() {
        JsonNode initial = call(admin, HttpMethod.GET, "/api/organization/privacy", null).getBody();
        assertThat(initial.get("organizationValue").isNull()).isTrue();
        assertThat(initial.get("blockedRetentionMonths").asInt()).isEqualTo(initial.get("defaultMonths").asInt());

        JsonNode changed = call(admin, HttpMethod.PUT, "/api/organization/privacy",
                Map.of("blockedRetentionMonths", 12)).getBody();
        assertThat(changed.get("blockedRetentionMonths").asInt()).isEqualTo(12);

        Person victim = person("VIEWER", "Lector " + unique());
        call(admin, HttpMethod.POST, "/api/users/" + victim.id() + "/block", null);
        Instant destroyAfter = ((java.sql.Timestamp) jdbc.queryForMap("SELECT destroy_after FROM "
                + "identity.blocked_identity WHERE user_id = ?", victim.id()).get("destroy_after")).toInstant();
        assertThat(destroyAfter).isBetween(Instant.now().plus(11 * 28L, ChronoUnit.DAYS),
                Instant.now().plus(13 * 31L, ChronoUnit.DAYS));

        assertThat(call(admin, HttpMethod.PUT, "/api/organization/privacy", Map.of("blockedRetentionMonths", 0))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(admin, HttpMethod.PUT, "/api/organization/privacy", Map.of("blockedRetentionMonths", 241))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Person auditor = person("AUDITOR", "Auditor " + unique());
        assertThat(call(auditor.token(), HttpMethod.GET, "/api/organization/privacy", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode trace = call(admin, HttpMethod.GET, "/api/trace?entityType=ORGANIZATION&size=20", null).getBody()
                .get("content");
        assertThat(trace.toString()).contains("plazo de conservación");
    }

    @Test
    void expiredBlockedDataIsDestroyedAndLeavesATraceEntry() {
        Person victim = person("VIEWER", "Lector " + unique());
        call(admin, HttpMethod.POST, "/api/users/" + victim.id() + "/block", null);
        assertThat(destroyer.destroyExpired(Instant.now())).isZero().as("aún no vence");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.blocked_identity WHERE user_id = ?",
                Integer.class, victim.id())).isEqualTo(1);

        jdbc.update("UPDATE identity.blocked_identity SET destroy_after = now() - interval '1 day' WHERE user_id = ?",
                victim.id());
        assertThat(destroyer.destroyExpired(Instant.now())).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.blocked_identity WHERE user_id = ?",
                Integer.class, victim.id())).isZero();
        JsonNode trace = call(admin, HttpMethod.GET, "/api/trace?user=sistema&size=50", null).getBody().get("content");
        assertThat(trace.toString()).contains("Destruyó los datos identificativos bloqueados del usuario n.º "
                + victim.id());
        assertThat(call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody().get("ok").asBoolean()).isTrue();
    }

    // ---------------------------------------------------------------- traza sin nombres

    @Test
    void newTraceEntriesStoreNoNamesAndOldFormatEntriesStillVerify() {
        call(admin, HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique(),
                "contactName", "Persona de Contacto Secreta", "contactEmail", "secreto@ejemplo.test"));
        Map<String, Object> last = jdbc.queryForMap("SELECT username, user_full_name, format_version, changes FROM "
                + "trace.change_log ORDER BY seq DESC LIMIT 1");
        assertThat(last.get("username")).isNull();
        assertThat(last.get("user_full_name")).isNull();
        assertThat(((Number) last.get("format_version")).intValue()).isEqualTo(2);
        // Los datos personales del contacto no se guardan en la traza: solo consta que se informaron.
        assertThat((String) last.get("changes")).doesNotContain("Secreta").doesNotContain("secreto@ejemplo.test")
                .contains("(dato personal informado)");

        // Una entrada en el formato anterior (con nombres) sigue verificándose con su fórmula original.
        Long org = jdbc.queryForObject("SELECT organization_id FROM identity.app_users WHERE email = 'admin@demo.test'",
                Long.class);
        transactions.executeWithoutResult(status -> {
            TraceRepository.Head head = traceRepository.lockHead(org, TraceCrypto.GENESIS_HASH);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            StoredEntry draft = new StoredEntry(null, org, head.lastSeq() + 1, now, now, 1L, "viejo@demo.test",
                    "Nombre Antiguo", "ADMIN", "FARM", 1L, "Finca antigua", null, null, null, "UPDATE",
                    "Entrada del formato anterior", "[]", head.lastHash(), null, null, 1);
            String hash = crypto.hash(draft.canonical());
            traceRepository.insert(new StoredEntry(null, org, draft.seq(), now, now, 1L, "viejo@demo.test",
                    "Nombre Antiguo", "ADMIN", "FARM", 1L, "Finca antigua", null, null, null, "UPDATE",
                    "Entrada del formato anterior", "[]", head.lastHash(), hash, crypto.sign(hash), 1));
        });
        call(admin, HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique()));
        JsonNode report = call(admin, HttpMethod.GET, "/api/trace/verify", null).getBody();
        assertThat(report.get("ok").asBoolean()).as(report.toString()).isTrue();
    }

    // ---------------------------------------------------------------- exportación de los datos de la organización

    @Test
    void theOrganizationDataIsExportedAsAZipOnlyByAdministrationAndOnlyItsOwn() throws IOException {
        long farm = id(call(admin, HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca Exportada " + unique())));
        long audit = id(call(admin, HttpMethod.POST, "/api/audits", Map.of("farmId", farm,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", "INTERNAL", "leadAuditor", "Alguien")));
        assertThat(uploadPng(admin, "/api/audits/" + audit + "/attachments").getStatusCode().is2xxSuccessful()).isTrue();

        // Datos de otra organización: no deben aparecer.
        Long otherOrg = jdbc.queryForObject("INSERT INTO identity.organizations (name) VALUES ('Cooperativa ajena') "
                + "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id", Long.class);
        jdbc.update("INSERT INTO fieldbook.farms (organization_id, legal_name) VALUES (?, 'Finca de otra cooperativa')",
                otherOrg);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(admin);
        ResponseEntity<byte[]> zip = rest.exchange("/api/organization/export", HttpMethod.GET, new HttpEntity<>(h),
                byte[].class);
        assertThat(zip.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(zip.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("datos-organizacion-");

        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.getBody()))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                files.put(e.getName(), in.readAllBytes());
            }
        }
        assertThat(files.keySet()).contains("LEEME.txt", "datos/organizacion.json", "datos/usuarios.json",
                "datos/explotaciones.json", "datos/auditorias.json", "datos/resultados-checklist.json",
                "datos/no-conformidades.json", "datos/adjuntos.json", "datos/documentos-generados.json",
                "datos/estandares-propios.json", "datos/propuestas-ia.json", "datos/traza-de-cambios.json",
                "datos/traza-sellos.json");
        String all = new String(files.get("datos/explotaciones.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(all).contains("Finca Exportada").doesNotContain("Finca de otra cooperativa");
        String users = new String(files.get("datos/usuarios.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(users).contains("admin@demo.test").doesNotContain("password");
        // El adjunto viaja tal cual.
        String attachmentName = files.keySet().stream().filter(n -> n.startsWith("adjuntos/")
                && n.endsWith("-foto.png")).findFirst().orElseThrow();
        assertThat(files.get(attachmentName)).isEqualTo(PNG);

        // Solo la administración exporta, y queda constancia en la traza.
        Person auditor = person("AUDITOR", "Auditor " + unique());
        HttpHeaders ah = new HttpHeaders();
        ah.setBearerAuth(auditor.token());
        assertThat(rest.exchange("/api/organization/export", HttpMethod.GET, new HttpEntity<>(ah), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode trace = call(admin, HttpMethod.GET, "/api/trace?entityType=ORGANIZATION&size=20", null).getBody()
                .get("content");
        assertThat(trace.toString()).contains("Exportó todos los datos de la organización");
        List<String> unused = new ArrayList<>(files.keySet());
        assertThat(unused).isNotEmpty();
    }

    @Test
    void personalDataMarkOnAttachmentsIsVisibleOnlyToAdminQualityAndUploader() {
        Person ana = person("AUDITOR", "Ana Adjuntos " + unique());
        Person otherAuditor = person("AUDITOR", "Otro Auditor " + unique());
        Person quality = person("QUALITY", "Calidad Adjuntos " + unique());
        long farm = id(call(ana.token(), HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique())));
        long audit = id(call(ana.token(), HttpMethod.POST, "/api/audits", Map.of("farmId", farm,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", "INTERNAL", "leadAuditor", ana.fullName())));

        ResponseEntity<JsonNode> up = uploadPng(ana.token(), "/api/audits/" + audit + "/attachments", true);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long attachment = up.getBody().get("id").asLong();
        assertThat(up.getBody().get("containsPersonalData").asBoolean()).isTrue();
        long plain = id(uploadPng(ana.token(), "/api/audits/" + audit + "/attachments"));

        // Administración, calidad y quien lo subió ven la marca; el resto no ve nada (ni sí ni no).
        for (String token : List.of(admin, quality.token(), ana.token())) {
            JsonNode list = call(token, HttpMethod.GET, "/api/audits/" + audit + "/attachments", null).getBody();
            assertThat(list.get(0).get("containsPersonalData").asBoolean()).isTrue();
            assertThat(list.get(1).get("containsPersonalData").asBoolean()).isFalse();
        }
        JsonNode hidden = call(otherAuditor.token(), HttpMethod.GET, "/api/audits/" + audit + "/attachments", null)
                .getBody();
        assertThat(hidden.get(0).get("containsPersonalData").isNull()).isTrue();
        assertThat(hidden.get(1).get("containsPersonalData").isNull()).isTrue();

        // Quien no puede verla tampoco puede cambiarla.
        assertThat(call(otherAuditor.token(), HttpMethod.PUT, "/api/attachments/" + attachment + "/personal-data",
                Map.of("containsPersonalData", false)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Calidad la quita y queda en la traza.
        ResponseEntity<JsonNode> cleared = call(quality.token(), HttpMethod.PUT,
                "/api/attachments/" + attachment + "/personal-data", Map.of("containsPersonalData", false));
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("containsPersonalData").asBoolean()).isFalse();
        assertThat(call(admin, HttpMethod.GET, "/api/trace?entityType=ATTACHMENT&size=50", null).getBody()
                .get("content").toString()).contains("contiene datos personales");
        assertThat(plain).isNotEqualTo(attachment);
    }

    // ---------------------------------------------------------------- lista de bloqueados, exportación de una persona, inventario

    private Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                files.put(e.getName(), in.readAllBytes());
            }
        }
        return files;
    }

    private String text(Map<String, byte[]> files, String name) {
        assertThat(files).containsKey(name);
        return new String(files.get(name), java.nio.charset.StandardCharsets.UTF_8);
    }

    private ResponseEntity<byte[]> personExport(String token, long userId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return rest.exchange("/api/users/" + userId + "/data-export", HttpMethod.GET, new HttpEntity<>(h), byte[].class);
    }

    @Test
    void theBlockedUsersListShowsDatesButNeverNamesOrEmails() {
        Person ana = person("AUDITOR", "Ana Listada " + unique());
        assertThat(call(admin, HttpMethod.POST, "/api/users/" + ana.id() + "/block", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> list = call(admin, HttpMethod.GET, "/api/organization/privacy/blocked", null);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode row = null;
        for (JsonNode r : list.getBody()) {
            if (r.get("userId").asLong() == ana.id()) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("role").asText()).isEqualTo("AUDITOR");
        assertThat(row.get("blockedAt").asText()).isNotBlank();
        assertThat(row.get("destroyAfter").asText()).isNotBlank();
        assertThat(row.get("blockedBy").asText()).isNotBlank();
        assertThat(list.getBody().toString()).doesNotContain(ana.email()).doesNotContain(ana.fullName());

        Person auditor = person("AUDITOR", "Auditor " + unique());
        assertThat(call(auditor.token(), HttpMethod.GET, "/api/organization/privacy/blocked", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void blockingAlsoReplacesTheLeadAuditorTheTeamTextAndTheResponsiblePerson() {
        String name = "Marta Sustituible " + unique();
        Person marta = person("AUDITOR", name);
        long farm = id(call(admin, HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique())));
        long audit = id(call(admin, HttpMethod.POST, "/api/audits", Map.of("farmId", farm,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", "INTERNAL", "leadAuditor", name,
                "auditTeam", "Pedro Otro, " + name + ", Lucía Más")));
        long cp = call(admin, HttpMethod.GET, "/api/audits/" + audit + "/results", null).getBody().get(0)
                .get("controlPointId").asLong();
        call(admin, HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results",
                List.of(Map.of("controlPointId", cp, "compliance", "NON_COMPLIANT"))));
        long finding = id(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/findings",
                Map.of("controlPointId", cp, "classification", "MINOR", "title", "NC")));
        id(call(admin, HttpMethod.POST, "/api/audits/" + audit + "/findings/" + finding + "/corrective-actions",
                Map.of("rootCause", "Causa", "responsiblePerson", name)));

        assertThat(call(admin, HttpMethod.POST, "/api/users/" + marta.id() + "/block", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("SELECT lead_auditor FROM audit.audits WHERE id = ?", String.class, audit))
                .isEqualTo("Usuario bloqueado");
        assertThat(jdbc.queryForObject("SELECT audit_team FROM audit.audits WHERE id = ?", String.class, audit))
                .isEqualTo("Pedro Otro, Usuario bloqueado, Lucía Más");
        assertThat(jdbc.queryForObject("SELECT responsible_person FROM findings.corrective_actions "
                + "WHERE finding_id = ?", String.class, finding)).isEqualTo("Usuario bloqueado");
    }

    @Test
    void thePersonDataExportContainsOnlyThatPersonsDataAndIsAdministrationOnly() throws IOException {
        String name = "Ana Acceso " + unique();
        Person ana = person("AUDITOR", name);
        Person other = person("AUDITOR", "Otra Persona " + unique());
        long farm = id(call(ana.token(), HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca " + unique())));
        long audit = id(call(ana.token(), HttpMethod.POST, "/api/audits", Map.of("farmId", farm,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", "INTERNAL", "leadAuditor", name)));
        long cp = call(ana.token(), HttpMethod.GET, "/api/audits/" + audit + "/results", null).getBody().get(0)
                .get("controlPointId").asLong();
        call(ana.token(), HttpMethod.PUT, "/api/audits/" + audit + "/results", Map.of("results",
                List.of(Map.of("controlPointId", cp, "compliance", "COMPLIANT", "auditorComment", "comentario privado"))));
        assertThat(uploadPng(ana.token(), "/api/audits/" + audit + "/attachments", true).getStatusCode()
                .is2xxSuccessful()).isTrue();
        // Algo hecho por otra persona: no debe aparecer.
        long otherFarm = id(call(other.token(), HttpMethod.POST, "/api/farms", Map.of("legalName", "Finca de otra " + unique())));
        long otherAudit = id(call(other.token(), HttpMethod.POST, "/api/audits", Map.of("farmId", otherFarm,
                "standardCode", "GLOBALGAP_IFA_V6", "auditType", "INTERNAL", "leadAuditor", other.fullName())));

        ResponseEntity<byte[]> zip = personExport(admin, ana.id());
        assertThat(zip.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(zip.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("datos-usuario-" + ana.id());
        Map<String, byte[]> files = unzip(zip.getBody());

        assertThat(files.keySet()).contains("LEEME.txt", "datos/cuenta-de-usuario.json",
                "datos/auditorias-en-las-que-participa.json", "datos/puntos-evaluados.json",
                "datos/adjuntos-subidos.json", "datos/documentos-generados.json", "datos/estandares-importados.json",
                "datos/propuestas-ia-revisadas.json", "datos/acciones-correctivas.json", "datos/historial-de-cambios.json");
        String account = text(files, "datos/cuenta-de-usuario.json");
        assertThat(account).contains(ana.email()).contains(name).doesNotContain("password");
        String audits = text(files, "datos/auditorias-en-las-que-participa.json");
        assertThat(audits).contains("auditor jefe");
        assertThat(audits).doesNotContain(other.fullName());
        assertThat(text(files, "datos/puntos-evaluados.json")).contains("COMPLIANT").doesNotContain("comentario privado");
        assertThat(text(files, "datos/adjuntos-subidos.json")).contains("foto.png");
        assertThat(text(files, "datos/historial-de-cambios.json")).contains("Creó").doesNotContain(other.fullName());
        assertThat(otherAudit).isNotEqualTo(audit);

        // Solo administración, y queda constancia en la traza.
        assertThat(personExport(ana.token(), ana.id()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(admin, HttpMethod.GET, "/api/trace?entityType=USER&size=50", null).getBody().get("content")
                .toString()).contains("derecho de acceso");
        // Un usuario inexistente o con los datos bloqueados no se exporta.
        assertThat(personExport(admin, 999_999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        call(admin, HttpMethod.POST, "/api/users/" + ana.id() + "/block", null);
        assertThat(personExport(admin, ana.id()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void everyFieldListedInThePersonalDataInventoryReallyExistsInTheDatabase() {
        ResponseEntity<JsonNode> inventory = call(admin, HttpMethod.GET, "/api/organization/privacy/inventory", null);
        assertThat(inventory.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inventory.getBody().size()).isGreaterThan(5);
        for (JsonNode item : inventory.getBody()) {
            assertThat(item.get("category").asText()).isNotBlank();
            for (JsonNode field : item.get("fields")) {
                String[] parts = field.asText().split("\\.");
                assertThat(parts).as("campo %s", field).hasSize(3);
                Integer found = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ?", Integer.class,
                        parts[0], parts[1], parts[2]);
                assertThat(found).as("columna %s", field).isEqualTo(1);
            }
        }
        Person auditor = person("AUDITOR", "Auditor " + unique());
        assertThat(call(auditor.token(), HttpMethod.GET, "/api/organization/privacy/inventory", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
