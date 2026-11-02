package com.agriaudit.identity.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSafetyCheckTest {

    private static final String STRONG_SECRET = "k7Jx2Qm9vTz4RbN8sLw3Yp6HdAe1UcFg5XoVi0MqZtBn7KyW";

    @Test
    void rechazaSecretosYCuentasDeDemostracion() {
        List<String> problems = problems("dev-only-secret-please-change-this-to-a-long-random-string-4f8a1",
                "Admin123!", true, "dev-only-trace-hmac-key-change-me-in-production-0123456789abcdef", "");

        assertThat(problems).hasSize(5);
    }

    @Test
    void rechazaUnSecretoCortoOUnaClaveDeAdminDebil() {
        assertThat(problems("corto", "corta", false, STRONG_SECRET, "60")).hasSize(2);
    }

    @Test
    void aceptaUnaConfiguracionPropia() {
        assertThat(problems(STRONG_SECRET, "Una-clave-larga-y-propia-9", false, STRONG_SECRET, "60")).isEmpty();
    }

    private static List<String> problems(String secret, String password, boolean demo, String traceKey,
                                         String retention) {
        return ProductionSafetyCheck.problems(secret, password, demo, traceKey, retention);
    }
}
