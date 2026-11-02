package com.agriaudit.ai.infrastructure.fake;

import com.agriaudit.ai.application.AiSettings;
import com.agriaudit.ai.domain.LlmRequest;
import com.agriaudit.ai.domain.LlmResponse;
import com.agriaudit.ai.port.LlmPort;

/**
 * Doble determinista de {@link LlmPort} ({@code provider=fake}) para tests y CI: no llama a ningún servicio
 * ni necesita clave. Devuelve un eco de la instrucción (JSON si se pidió salida estructurada) y un recuento
 * de tokens aproximado (1 token ≈ 4 caracteres).
 */
public class FakeLlmAdapter implements LlmPort {

    private final AiSettings settings;

    public FakeLlmAdapter(AiSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String text = request.jsonSchema() == null
                ? "[fake] " + request.instruction()
                : "{\"fake\":true,\"instruction\":" + quote(request.instruction()) + "}";
        String model = settings.tiers().containsKey(request.tier()) ? settings.tier(request.tier()).model() : "fake";
        long in = (request.systemPrompt().length() + request.userMessage().length()) / 4 + 1;
        return new LlmResponse(text, false, model, in, text.length() / 4 + 1, 0, null);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
