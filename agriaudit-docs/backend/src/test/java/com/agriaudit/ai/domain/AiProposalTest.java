package com.agriaudit.ai.domain;

import com.agriaudit.common.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProposalTest {

    private static final Instant T0 = Instant.parse("2026-09-13T10:00:00Z");

    @Test
    void nacePendienteYNoEsAplicable() {
        AiProposal p = proposal();

        assertThat(p.status()).isEqualTo(AiProposalStatus.PENDING);
        assertThat(p.isApplicable()).isFalse();
        assertThat(p.reviewedBy()).isNull();
    }

    @Test
    void aceptarLaHaceAplicableYRegistraRevisor() {
        AiProposal p = proposal();
        p.accept("ana@demo.test", T0);

        assertThat(p.status()).isEqualTo(AiProposalStatus.ACCEPTED);
        assertThat(p.isApplicable()).isTrue();
        assertThat(p.reviewedBy()).isEqualTo("ana@demo.test");
        assertThat(p.effectiveOutput()).isEqualTo("salida original");
    }

    @Test
    void aceptarEditadaConservaOriginalYAplicaLaEdicion() {
        AiProposal p = proposal();
        p.acceptEdited("ana@demo.test", "salida corregida", T0);

        assertThat(p.status()).isEqualTo(AiProposalStatus.EDITED);
        assertThat(p.output()).isEqualTo("salida original");
        assertThat(p.effectiveOutput()).isEqualTo("salida corregida");
    }

    @Test
    void unaPropuestaRevisadaNoAdmiteOtraRevision() {
        AiProposal p = proposal();
        p.reject("ana@demo.test", T0);

        assertThat(p.isApplicable()).isFalse();
        assertThatThrownBy(() -> p.accept("otro@demo.test", T0)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> p.reject("otro@demo.test", T0)).isInstanceOf(ConflictException.class);
    }

    @Test
    void laRevisionExigeRevisorYEdicionNoVacia() {
        assertThatThrownBy(() -> proposal().accept(" ", T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> proposal().acceptEdited("ana@demo.test", "", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AiProposal proposal() {
        return AiProposal.pending(1L, AiAssistant.DIAGNOSTIC, "attachment:9", "diagnostic.v1", "salida original",
                new AiUsage("claude-haiku-4-5", 100, 20, 350, new BigDecimal("0.0002"), null), T0);
    }
}
