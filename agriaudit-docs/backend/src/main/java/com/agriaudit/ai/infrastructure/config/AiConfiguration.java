package com.agriaudit.ai.infrastructure.config;

import com.agriaudit.ai.application.AiSettings;
import com.agriaudit.ai.port.LlmPort;
import com.agriaudit.ai.infrastructure.anthropic.AnthropicLlmAdapter;
import com.agriaudit.ai.infrastructure.fake.FakeEmbeddingAdapter;
import com.agriaudit.ai.infrastructure.fake.FakeLlmAdapter;
import com.agriaudit.ai.port.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Elige el adaptador de {@link LlmPort} según {@code agriaudit.ai.provider} (RT-A01). */
@Configuration
@EnableConfigurationProperties(AiSettings.class)
class AiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    LlmPort llmPort(AiSettings settings) {
        LlmPort port = switch (settings.provider()) {
            case "anthropic" -> new AnthropicLlmAdapter(settings);
            case "fake" -> new FakeLlmAdapter(settings);
            case "none" -> DisabledLlmAdapter.INSTANCE;
            default -> throw new IllegalStateException(
                    "agriaudit.ai.provider no válido: '" + settings.provider() + "' (none | fake | anthropic)");
        };
        log.info("Asistentes de IA: proveedor={} disponible={}", settings.provider(), port.isAvailable());
        return port;
    }

    /** {@code agriaudit.ai.embeddings.provider}: none (por defecto) | fake. El proveedor real se elige en WS-5. */
    @Bean
    EmbeddingPort embeddingPort(@Value("${agriaudit.ai.embeddings.provider:none}") String provider) {
        return switch (provider) {
            case "fake" -> new FakeEmbeddingAdapter();
            case "none" -> DisabledEmbeddingAdapter.INSTANCE;
            default -> throw new IllegalStateException(
                    "agriaudit.ai.embeddings.provider no válido: '" + provider + "' (none | fake)");
        };
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
