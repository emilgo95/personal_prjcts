package com.agriaudit;

import com.agriaudit.support.TestPostgres;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Premisa de la excepción de CodeQL {@code java/spring-disabled-csrf-protection}
 * ({@code .github/codeql-exceptions.json}): la API se autentica solo con la cabecera
 * {@code Authorization: Bearer}, sin cookies ni sesión. El navegador no añade esa cabecera por su cuenta
 * en una petición entre sitios, así que no hay CSRF que proteger. Si este test falla, la protección CSRF
 * vuelve a ser necesaria y la excepción debe retirarse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class StatelessAuthenticationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = TestPostgres.container();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("agriaudit.storage.dir", () -> System.getProperty("java.io.tmpdir") + "/agriaudit-it-stateless");
        registry.add("agriaudit.admin.password", () -> "Admin123!");
        registry.add("agriaudit.cors.allowed-origins", () -> "http://localhost:8080");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void neitherLoginNorAuthenticatedRequestsSetCookies() {
        ResponseEntity<JsonNode> login = login();
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();

        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(login.getBody().get("accessToken").asText());
        ResponseEntity<String> audits = rest.exchange("/api/audits", HttpMethod.GET, new HttpEntity<>(bearer), String.class);
        assertThat(audits.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(audits.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
    }

    @Test
    void aCrossSiteRequestWithCookiesButWithoutBearerIsRejected() {
        HttpHeaders crossSite = new HttpHeaders();
        crossSite.setContentType(MediaType.APPLICATION_JSON);
        crossSite.add(HttpHeaders.ORIGIN, "https://sitio-malicioso.example");
        crossSite.add(HttpHeaders.COOKIE, "JSESSIONID=robada; XSRF-TOKEN=robado");

        ResponseEntity<String> response = rest.exchange("/api/farms", HttpMethod.POST,
                new HttpEntity<>(Map.of("legalName", "Finca creada por CSRF"), crossSite), String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void corsDoesNotAllowCredentials() {
        HttpHeaders preflight = new HttpHeaders();
        preflight.add(HttpHeaders.ORIGIN, "http://localhost:8080");
        preflight.add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");

        ResponseEntity<String> response = rest.exchange("/api/farms", HttpMethod.OPTIONS,
                new HttpEntity<>(preflight), String.class);

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:8080");
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isFalse();
    }

    private ResponseEntity<JsonNode> login() {
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "admin@demo.test", "password", "Admin123!"), json), JsonNode.class);
    }
}
