package com.agriaudit.support;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Primer acceso de un usuario creado por un administrador: su contraseña es temporal y hay que cambiarla antes de
 * usar la aplicación. Los tests que solo necesitan un usuario con un rol pasan por aquí.
 */
public final class FirstLogin {

    /** Contraseña que queda tras el primer acceso: la temporal más este sufijo. */
    public static final String CHANGED_SUFFIX = "-nueva";

    private FirstLogin() {
    }

    /** Inicia sesión con la contraseña temporal, la cambia y devuelve el token de acceso válido. */
    public static String withTemporaryPassword(TestRestTemplate rest, String email, String temporaryPassword) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                new HttpEntity<>(Map.of("email", email, "password", temporaryPassword), h), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("user").get("mustChangePassword").asBoolean()).isTrue();

        h.setBearerAuth(login.getBody().get("accessToken").asText());
        ResponseEntity<JsonNode> changed = rest.exchange("/api/auth/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", temporaryPassword,
                        "newPassword", temporaryPassword + CHANGED_SUFFIX), h), JsonNode.class);
        assertThat(changed.getStatusCode()).as("cambio de contraseña: %s", changed.getBody()).isEqualTo(HttpStatus.OK);
        return changed.getBody().get("accessToken").asText();
    }
}
