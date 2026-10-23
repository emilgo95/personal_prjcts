package com.agriaudit.identity.infrastructure.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Con el perfil {@code prod} la aplicación se niega a arrancar si conserva los secretos o las cuentas de
 * demostración que vienen por defecto (secreto JWT público, contraseña de administrador conocida, datos de demo).
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    static final int MIN_SECRET_LENGTH = 48;
    static final String DEFAULT_ADMIN_PASSWORD = "Admin123!";

    public ProductionSafetyCheck(@Value("${agriaudit.jwt.secret}") String jwtSecret,
                                 @Value("${agriaudit.admin.password}") String adminPassword,
                                 @Value("${agriaudit.demo.seed:true}") boolean demoSeed) {
        List<String> problems = problems(jwtSecret, adminPassword, demoSeed);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Configuración insegura para producción: " + String.join("; ", problems));
        }
    }

    static List<String> problems(String jwtSecret, String adminPassword, boolean demoSeed) {
        List<String> problems = new ArrayList<>();
        String lower = jwtSecret == null ? "" : jwtSecret.toLowerCase();
        if (jwtSecret == null || jwtSecret.length() < MIN_SECRET_LENGTH
                || lower.contains("dev-only") || lower.contains("change-me") || lower.contains("change-this")) {
            problems.add("JWT_SECRET debe ser una cadena aleatoria propia de al menos " + MIN_SECRET_LENGTH
                    + " caracteres");
        }
        if (adminPassword == null || adminPassword.length() < 12 || DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            problems.add("ADMIN_PASSWORD debe definirse con al menos 12 caracteres y no puede ser la de demostración");
        }
        if (demoSeed) {
            problems.add("AGRIAUDIT_DEMO_SEED debe ser false (no se pueden crear usuarios ni datos de demostración)");
        }
        return problems;
    }
}
