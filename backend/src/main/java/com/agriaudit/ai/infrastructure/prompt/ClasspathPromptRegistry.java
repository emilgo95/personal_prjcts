package com.agriaudit.ai.infrastructure.prompt;

import com.agriaudit.ai.application.AiSettings;
import com.agriaudit.ai.domain.PromptTemplate;
import com.agriaudit.ai.port.PromptRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompts versionados como recursos del repositorio (RT-A10): {@code resources/ai/prompts/<id>.v<N>.md}.
 * La versión activa de cada prompt se fija en {@code agriaudit.ai.prompts.<id>=N}; cambiar un prompt es un
 * PR con un fichero nuevo y su corrida de evals, nunca una edición en base de datos.
 */
@Component
class ClasspathPromptRegistry implements PromptRegistry {

    static final String LOCATION = "ai/prompts/";

    private final AiSettings settings;
    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    ClasspathPromptRegistry(AiSettings settings) {
        this.settings = settings;
    }

    @Override
    public PromptTemplate active(String promptId) {
        Integer version = settings.prompts().get(promptId);
        if (version == null) {
            throw new IllegalStateException("Prompt sin versión activa configurada: agriaudit.ai.prompts." + promptId);
        }
        return cache.computeIfAbsent(promptId + ".v" + version, key -> load(promptId, version));
    }

    private static PromptTemplate load(String promptId, int version) {
        ClassPathResource resource = new ClassPathResource(LOCATION + promptId + ".v" + version + ".md");
        if (!resource.exists()) {
            throw new IllegalStateException("No existe el prompt " + resource.getPath());
        }
        try {
            return new PromptTemplate(promptId, version, resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el prompt " + resource.getPath(), e);
        }
    }
}
