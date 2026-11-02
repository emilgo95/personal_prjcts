package com.agriaudit.audit.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditSearchTest {

    @Test
    void ignoresCaseAndAccentsAndSplitsIntoWords() {
        assertThat(AuditSearch.terms("  Cítricos   VEGA ")).containsExactly("citricos", "vega");
        assertThat(AuditSearch.terms("   ")).isEmpty();
        assertThat(AuditSearch.terms(null)).isEmpty();
    }

    @Test
    void everyWordMustAppearInAnyFieldAndOrder() {
        String text = AuditSearch.text("AUD-2026-0004", "Cítricos Vega Media, S. Coop.", null, "Enviada");
        assertThat(AuditSearch.matches(text, List.of("vega", "enviada"))).isTrue();
        assertThat(AuditSearch.matches(text, AuditSearch.terms("enviada citricos 0004"))).isTrue();
        assertThat(AuditSearch.matches(text, AuditSearch.terms("vega cerrada"))).isFalse();
    }

    @Test
    void longQueriesAreCut() {
        assertThat(String.join("", AuditSearch.terms("a".repeat(500)))).hasSize(AuditSearch.MAX_QUERY);
    }
}
