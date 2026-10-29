package com.agriaudit.trace.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StoredEntryCanonicalTest {

    private static final Instant T = Instant.parse("2026-10-29T10:00:00Z");

    private static StoredEntry entry(String username, String fullName, int version) {
        return new StoredEntry(null, 1, 5, T, T, 7L, username, fullName, "AUDITOR", "AUDIT", 3L, "AUD-1", null, null,
                3L, "UPDATE", "Resumen", "[]", "0".repeat(128), "h", "s", version);
    }

    @Test
    void elFormatoAnteriorIncluyeElNombreEnLaHuella() {
        assertThat(entry("a@x.test", "Ana", 1).canonical()).contains("a@x.test").contains("Ana");
        assertThat(entry("a@x.test", "Ana", 1).canonical()).isNotEqualTo(entry("b@x.test", "Ana", 1).canonical());
    }

    @Test
    void elFormato2NoDependeDelNombreNiDelCorreo() {
        assertThat(entry("a@x.test", "Ana", 2).canonical()).isEqualTo(entry("b@x.test", "Otra", 2).canonical());
        assertThat(entry(null, null, 2).canonical()).doesNotContain("a@x.test").doesNotContain("Ana");
    }

    @Test
    void laVersionQuedaAtadaALaHuella() {
        assertThat(entry(null, null, 2).canonical()).isNotEqualTo(entry(null, null, 1).canonical());
    }
}
