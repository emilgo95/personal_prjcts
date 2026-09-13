package com.agriaudit;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.core.ApplicationModules;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificación del monolito modular con Spring Modulith (RT-00, RT-02).
 *
 * <p>La reorganización se hace módulo a módulo (WS-1). Mientras conviven paquetes por capas
 * ({@code service}, {@code web}, {@code repo}, {@code domain}…) y módulos ya extraídos, el test está en
 * <b>modo aviso</b>: registra todas las infracciones, pero solo falla por las que afectan a un módulo
 * ya migrado ({@link #MIGRATED_MODULES}). Al terminar WS-1 se sustituye por
 * {@code ApplicationModules.verify()} a secas; también se puede forzar ya con
 * {@code -Dagriaudit.modulith.strict=true}.
 */
class ModularityTests {

    private static final Logger log = LoggerFactory.getLogger(ModularityTests.class);

    /** Módulos extraídos: nadie puede acceder a sus internals ni formar ciclos con ellos. */
    static final Set<String> MIGRATED_MODULES = Set.of("catalog", "common", "identity", "fieldbook", "storage");

    static final ApplicationModules modules = ApplicationModules.of(AgriAuditApplication.class);

    @Test
    void verifiesModularStructure() {
        if (Boolean.getBoolean("agriaudit.modulith.strict")) {
            modules.verify();
            return;
        }
        List<String> violations = modules.detectViolations().getMessages();
        violations.stream().map(ModularityTests::header).distinct()
                .forEach(v -> log.warn("[modulith, aviso] {}", v));
        log.warn("[modulith, aviso] {} infracciones pendientes de la reorganización (detalle: -Dagriaudit.modulith.strict=true)",
                violations.size());

        List<String> blocking = violations.stream().filter(ModularityTests::involvesMigratedModule).toList();
        assertThat(blocking)
                .as("Infracciones de límites que afectan a módulos ya migrados %s", MIGRATED_MODULES)
                .isEmpty();
    }

    /**
     * Mira solo la cabecera de la infracción — los módulos implicados ({@code Module 'x' depends on
     * non-exposed type … within module 'y'}) o las rebanadas del ciclo ({@code Slice x -> Slice y}) —,
     * no el detalle de dependencias, que menciona tipos de cualquier módulo.
     */
    static boolean involvesMigratedModule(String violation) {
        String header = header(violation);
        return MIGRATED_MODULES.stream().anyMatch(m ->
                header.contains("'" + m + "'") || header.matches("(?s).*\\bSlice " + m + "\\b.*"));
    }

    static String header(String violation) {
        int details = violation.indexOf("1. Dependencies");
        String head = details >= 0 ? violation.substring(0, details) : violation.lines().findFirst().orElse("");
        return head.replaceAll("\\s+", " ").replaceAll("\\$\\w+", "").trim();
    }
}
