package com.agriaudit.ai.infrastructure.anthropic;

import com.agriaudit.ai.application.AiSettings;
import com.agriaudit.ai.domain.LlmRequest;
import com.agriaudit.ai.domain.LlmResponse;
import com.agriaudit.ai.port.LlmPort;
import com.agriaudit.common.ServiceUnavailableException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Adaptador de {@link LlmPort} sobre la Claude API con el SDK oficial {@code anthropic-java}
 * ({@code provider=anthropic}). Único punto del código que conoce el SDK (RT-A01, lo verifica ArchUnit).
 *
 * <ul>
 *   <li>Clave solo desde el entorno ({@code ANTHROPIC_API_KEY}, RS-IA-09); sin clave, no está disponible.</li>
 *   <li>Modelo y región de inferencia por escalón, desde configuración (RT-A07, RS-IA-02).</li>
 *   <li>Salida estructurada con {@code output_config.format} cuando la petición trae esquema JSON.</li>
 *   <li>Cualquier fallo del proveedor se traduce a {@link ServiceUnavailableException} (RS-IA-10).</li>
 * </ul>
 */
public class AnthropicLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmAdapter.class);

    private final AiSettings settings;
    private final AnthropicClient client;

    public AnthropicLlmAdapter(AiSettings settings) {
        this(settings, buildClient(settings, null));
    }

    /** Constructor para tests: permite apuntar el SDK a un servidor local. */
    public AnthropicLlmAdapter(AiSettings settings, String baseUrl) {
        this(settings, buildClient(settings, baseUrl));
    }

    private AnthropicLlmAdapter(AiSettings settings, AnthropicClient client) {
        this.settings = settings;
        this.client = client;
    }

    private static AnthropicClient buildClient(AiSettings settings, String baseUrl) {
        String apiKey = settings.anthropic().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("agriaudit.ai.provider=anthropic sin ANTHROPIC_API_KEY: los asistentes de IA quedan desactivados");
            return null;
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(settings.anthropic().timeoutSeconds()))
                .maxRetries(settings.anthropic().maxRetries());
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (client == null) {
            throw new ServiceUnavailableException("Los asistentes de IA no están configurados en este entorno");
        }
        AiSettings.Tier tier = settings.tier(request.tier());
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(tier.model())
                .maxTokens(request.maxTokens())
                .system(request.systemPrompt())
                .addUserMessage(request.userMessage());
        if (tier.inferenceGeo() != null && !tier.inferenceGeo().isBlank()) {
            params.inferenceGeo(tier.inferenceGeo());
        }
        if (request.jsonSchema() != null) {
            JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
            request.jsonSchema().forEach((k, v) -> schema.putAdditionalProperty(k, JsonValue.from(v)));
            params.outputConfig(OutputConfig.builder()
                    .format(JsonOutputFormat.builder().schema(schema.build()).build())
                    .build());
        }

        long start = System.nanoTime();
        Message message;
        try {
            message = client.messages().create(params.build());
        } catch (RateLimitException e) {
            log.warn("ai.provider rate-limit model={} status={}", tier.model(), e.statusCode());
            throw new ServiceUnavailableException("El proveedor de IA está saturado; inténtalo más tarde", e);
        } catch (AnthropicServiceException e) {
            log.error("ai.provider error model={} status={} type={}", tier.model(), e.statusCode(),
                    e.errorType().map(Object::toString).orElse("?"));
            throw new ServiceUnavailableException("El proveedor de IA no ha podido atender la petición", e);
        } catch (AnthropicIoException e) {
            log.warn("ai.provider unreachable model={}: {}", tier.model(), e.getMessage());
            throw new ServiceUnavailableException("No se puede contactar con el proveedor de IA", e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        boolean refused = message.stopReason().map(StopReason.REFUSAL::equals).orElse(false);
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
        return new LlmResponse(text, refused, message.model().asString(), message.usage().inputTokens(),
                message.usage().outputTokens(), latencyMs, message.usage().inferenceGeo().orElse(null));
    }
}
