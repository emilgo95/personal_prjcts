package com.agriaudit.ai.infrastructure.anthropic;

import com.agriaudit.ai.application.AiSettings;
import com.agriaudit.ai.domain.LlmRequest;
import com.agriaudit.ai.domain.LlmResponse;
import com.agriaudit.ai.domain.ModelTier;
import com.agriaudit.common.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El adaptador contra un servidor HTTP local que imita la Messages API: sin red ni clave real, pero con la
 * serialización y el mapeo de errores reales del SDK (y con la versión de Jackson que fija Spring Boot).
 */
class AnthropicLlmAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<JsonNode> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            lastApiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            lastBody.set(JSON.readTree(exchange.getRequestBody()));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void enviaModeloRegionSistemaContenidoDelimitadoYEsquemaYMapeaLaRespuesta() {
        responseBody = message("{\\\"tipo\\\":\\\"ANALISIS_AGUA\\\"}", "end_turn");

        LlmResponse r = adapter().complete(new LlmRequest(ModelTier.BALANCED, "Eres un auditor.",
                "Clasifica el documento", "Boletín de análisis 12/03/2026", 512,
                Map.of("type", "object", "properties", Map.of("tipo", Map.of("type", "string")))));

        JsonNode body = lastBody.get();
        assertThat(lastApiKey.get()).isEqualTo("sk-ant-test");
        assertThat(body.path("model").asText()).isEqualTo("claude-sonnet-5");
        assertThat(body.path("inference_geo").asText()).isEqualTo("eu");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(512);
        assertThat(body.path("system").asText()).isEqualTo("Eres un auditor.");
        assertThat(body.path("messages").get(0).path("content").asText())
                .contains("<contenido_no_confiable>\nBoletín de análisis 12/03/2026\n</contenido_no_confiable>");
        assertThat(body.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("output_config").path("format").path("schema").path("properties").has("tipo")).isTrue();

        assertThat(r.text()).isEqualTo("{\"tipo\":\"ANALISIS_AGUA\"}");
        assertThat(r.refused()).isFalse();
        assertThat(r.modelId()).isEqualTo("claude-sonnet-5");
        assertThat(r.inputTokens()).isEqualTo(321);
        assertThat(r.outputTokens()).isEqualTo(12);
        assertThat(r.inferenceGeo()).isEqualTo("eu");
    }

    @Test
    void noEnviaRegionSiElEscalonNoLaTieneConfigurada() {
        responseBody = message("ok", "end_turn");

        adapter().complete(new LlmRequest(ModelTier.FAST, "s", "hola", null, 64, null));

        assertThat(lastBody.get().path("model").asText()).isEqualTo("claude-haiku-4-5");
        assertThat(lastBody.get().has("inference_geo")).isFalse();
        assertThat(lastBody.get().has("output_config")).isFalse();
    }

    @Test
    void detectaElRechazoDelModelo() {
        responseBody = message("", "refusal");

        LlmResponse r = adapter().complete(new LlmRequest(ModelTier.FAST, "s", "hola", null, 64, null));

        assertThat(r.refused()).isTrue();
    }

    @Test
    void losErroresDelProveedorSeTraducenAServicioNoDisponible() {
        status = 429;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}";
        assertThatThrownBy(() -> adapter().complete(new LlmRequest(ModelTier.FAST, "s", "hola", null, 64, null)))
                .isInstanceOf(ServiceUnavailableException.class);

        status = 400;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}";
        assertThatThrownBy(() -> adapter().complete(new LlmRequest(ModelTier.FAST, "s", "hola", null, 64, null)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void sinClaveNoEstaDisponible() {
        AiSettings noKey = new AiSettings("anthropic", settings().tiers(), Map.of(), Map.of(),
                new AiSettings.Anthropic("", 5, 0));

        assertThat(new AnthropicLlmAdapter(noKey).isAvailable()).isFalse();
        assertThatThrownBy(() -> new AnthropicLlmAdapter(noKey)
                .complete(new LlmRequest(ModelTier.FAST, "s", "hola", null, 64, null)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private AnthropicLlmAdapter adapter() {
        return new AnthropicLlmAdapter(settings(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static AiSettings settings() {
        return new AiSettings("anthropic",
                Map.of(ModelTier.FAST, new AiSettings.Tier("claude-haiku-4-5", ""),
                        ModelTier.BALANCED, new AiSettings.Tier("claude-sonnet-5", "eu")),
                Map.of(), Map.of(), new AiSettings.Anthropic("sk-ant-test", 5, 0));
    }

    private static String message(String text, String stopReason) {
        return """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-sonnet-5",
                 "content":[{"type":"text","text":"%s"}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":321,"output_tokens":12,"inference_geo":"eu"}}
                """.formatted(text, stopReason);
    }
}
