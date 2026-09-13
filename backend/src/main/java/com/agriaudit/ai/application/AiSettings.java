package com.agriaudit.ai.application;

import com.agriaudit.ai.domain.ModelTier;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Configuración de la IA ({@code agriaudit.ai.*}).
 *
 * @param provider {@code none} (por defecto: IA desactivada), {@code fake} (tests y CI) o {@code anthropic}
 * @param tiers    modelo y región de inferencia por escalón (RT-A07); la región vacía no se envía
 * @param pricing  tarifa por millón de tokens de cada modelo, para la traza de coste (RT-A08)
 * @param prompts  versión activa de cada prompt (RT-A10)
 */
@ConfigurationProperties(prefix = "agriaudit.ai")
public record AiSettings(String provider, Map<ModelTier, Tier> tiers, Map<String, Price> pricing,
                         Map<String, Integer> prompts, Anthropic anthropic) {

    public AiSettings {
        provider = provider == null || provider.isBlank() ? "none" : provider.trim();
        tiers = tiers == null ? Map.of() : Map.copyOf(tiers);
        pricing = pricing == null ? Map.of() : Map.copyOf(pricing);
        prompts = prompts == null ? Map.of() : Map.copyOf(prompts);
        anthropic = anthropic == null ? new Anthropic(null, 60, 2) : anthropic;
    }

    public Tier tier(ModelTier tier) {
        Tier t = tiers.get(tier);
        if (t == null || t.model() == null || t.model().isBlank()) {
            throw new IllegalStateException("Sin modelo configurado para el escalón " + tier);
        }
        return t;
    }

    /** @param inferenceGeo región de inferencia ({@code eu}, {@code us}); vacía si el modelo no la admite */
    public record Tier(String model, String inferenceGeo) {
    }

    public record Price(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    }

    /** @param apiKey solo desde el entorno ({@code ANTHROPIC_API_KEY}); nunca en el repositorio (RS-IA-09) */
    public record Anthropic(String apiKey, int timeoutSeconds, int maxRetries) {
    }
}
