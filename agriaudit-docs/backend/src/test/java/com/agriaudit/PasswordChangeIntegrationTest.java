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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contraseñas (fase 1 del alta de usuarios): la que pone un administrador es temporal y hay que cambiarla antes de
 * usar la aplicación; cada persona puede cambiar la suya; el administrador puede restablecerla; y al cambiarla se
 * cierran las demás sesiones abiertas.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PasswordChangeIntegrationTest {

    private static final String TEMPORARY = "Temporal-2026";

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-password");
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
        admin = login("admin@demo.test", "Admin123!").getBody().get("accessToken").asText();
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

    private ResponseEntity<JsonNode> login(String email, String password) {
        return rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", password), headers(null)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> call(String token, HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> changePassword(String token, String current, String next) {
        return call(token, HttpMethod.POST, "/api/auth/password",
                Map.of("currentPassword", current, "newPassword", next));
    }

    private ResponseEntity<JsonNode> refresh(String refreshToken) {
        return rest.postForEntity("/api/auth/refresh", new HttpEntity<>(Map.of("refreshToken", refreshToken),
                headers(null)), JsonNode.class);
    }

    /** Usuario nuevo creado por el administrador con la contraseña temporal; devuelve [id, correo]. */
    private Object[] newUser(String role) {
        String email = "pwd." + UUID.randomUUID().toString().substring(0, 8) + "@demo.test";
        ResponseEntity<JsonNode> r = call(admin, HttpMethod.POST, "/api/users",
                Map.of("email", email, "fullName", "Persona", "role", role, "password", TEMPORARY));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody().get("mustChangePassword").asBoolean()).isTrue();
        return new Object[] {r.getBody().get("id").asLong(), email};
    }

    // ---------------------------------------------------------------- primer acceso

    @Test
    void aTemporaryPasswordOnlyAllowsChangingItAndTheChangeClosesOtherSessions() {
        String email = (String) newUser("AUDITOR")[1];
        ResponseEntity<JsonNode> first = login(email, TEMPORARY);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("user").get("mustChangePassword").asBoolean()).isTrue();
        String temporaryToken = first.getBody().get("accessToken").asText();
        String temporaryRefresh = first.getBody().get("refreshToken").asText();

        // Con la temporal solo puede ver quién es y cambiarla.
        ResponseEntity<JsonNode> blocked = call(temporaryToken, HttpMethod.GET, "/api/farms", null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody().get("code").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(call(temporaryToken, HttpMethod.GET, "/api/auth/me", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // La actual mal escrita, una demasiado corta o repetir la misma: 400 (no 401, que cerraría la sesión).
        assertThat(changePassword(temporaryToken, "otra-cosa", "Nueva-clave-1").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(temporaryToken, TEMPORARY, "corta").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(temporaryToken, TEMPORARY, TEMPORARY).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(temporaryToken, TEMPORARY, "x".repeat(73)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> changed = changePassword(temporaryToken, TEMPORARY, "Nueva-clave-1");
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("user").get("mustChangePassword").asBoolean()).isFalse();
        String token = changed.getBody().get("accessToken").asText();

        // Ya puede trabajar con los tokens nuevos.
        assertThat(call(token, HttpMethod.GET, "/api/farms", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh(changed.getBody().get("refreshToken").asText()).getStatusCode()).isEqualTo(HttpStatus.OK);
        // La contraseña anterior ya no sirve y la nueva sí.
        assertThat(login(email, TEMPORARY).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, "Nueva-clave-1").getBody().get("user").get("mustChangePassword").asBoolean())
                .isFalse();
        // Las sesiones abiertas antes del cambio quedan cerradas (los tokens llevan la hora en segundos).
        waitNextSecond();
        changePassword(token, "Nueva-clave-1", "Otra-clave-2");
        assertThat(call(token, HttpMethod.GET, "/api/farms", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(temporaryRefresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void everyoneCanChangeTheirOwnPasswordAndItIsTracedWithoutTheValue() {
        Object[] user = newUser("VIEWER");
        String token = com.agriaudit.support.FirstLogin.withTemporaryPassword(rest, (String) user[1], TEMPORARY);

        assertThat(call(token, HttpMethod.GET, "/api/auth/me", null).getBody().get("mustChangePassword").asBoolean())
                .isFalse();
        Integer traced = jdbc.queryForObject("SELECT count(*) FROM trace.change_log WHERE entity_id = ? AND "
                + "summary LIKE 'Cambió su contraseña%'", Integer.class, user[0]);
        assertThat(traced).isEqualTo(1);
        Integer leaked = jdbc.queryForObject("SELECT count(*) FROM trace.change_log WHERE changes::text LIKE ?",
                Integer.class, "%" + TEMPORARY + "%");
        assertThat(leaked).isZero();
    }

    // ---------------------------------------------------------------- restablecer (administrador)

    @Test
    void anAdministratorResetsAPasswordWithATemporaryOneAndClosesTheUserSessions() {
        Object[] user = newUser("QUALITY");
        long id = (Long) user[0];
        String email = (String) user[1];
        String token = com.agriaudit.support.FirstLogin.withTemporaryPassword(rest, email, TEMPORARY);
        assertThat(call(token, HttpMethod.GET, "/api/farms", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        waitNextSecond();
        ResponseEntity<JsonNode> reset = call(admin, HttpMethod.POST, "/api/users/" + id + "/reset-password", null);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        String temporary = reset.getBody().get("temporaryPassword").asText();
        assertThat(temporary).matches("[A-Za-z2-9]{4}-[A-Za-z2-9]{4}-[A-Za-z2-9]{4}");

        // Su sesión se cierra y al entrar con la temporal tiene que cambiarla.
        assertThat(call(token, HttpMethod.GET, "/api/farms", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, temporary).getBody().get("user").get("mustChangePassword").asBoolean()).isTrue();
        assertThat(com.agriaudit.support.FirstLogin.withTemporaryPassword(rest, email, temporary)).isNotBlank();
    }

    @Test
    void onlyAnAdministratorResetsAndNeverTheirOwnOrABlockedUserPassword() {
        long myId = call(admin, HttpMethod.GET, "/api/auth/me", null).getBody().get("id").asLong();
        assertThat(call(admin, HttpMethod.POST, "/api/users/" + myId + "/reset-password", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Object[] auditor = newUser("AUDITOR");
        String auditorToken = com.agriaudit.support.FirstLogin.withTemporaryPassword(rest, (String) auditor[1],
                TEMPORARY);
        long other = (Long) newUser("VIEWER")[0];
        assertThat(call(auditorToken, HttpMethod.POST, "/api/users/" + other + "/reset-password", null)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(call(admin, HttpMethod.POST, "/api/users/" + other + "/block", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(admin, HttpMethod.POST, "/api/users/" + other + "/reset-password", null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /** Los tokens llevan la hora en segundos: se espera al segundo siguiente para que el cambio sea posterior. */
    private static void waitNextSecond() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
