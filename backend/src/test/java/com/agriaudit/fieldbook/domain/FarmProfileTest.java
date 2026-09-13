package com.agriaudit.fieldbook.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class FarmProfileTest {

    @Test
    void exigeRazonSocial() {
        assertThatThrownBy(() -> profile(" ", null, null, null)).isInstanceOf(InvalidFarmException.class);
        assertThatThrownBy(() -> profile(null, null, null, null)).isInstanceOf(InvalidFarmException.class);
    }

    @Test
    void rechazaSuperficieNegativa() {
        assertThatThrownBy(() -> profile("Finca", null, null, new BigDecimal("-1")))
                .isInstanceOf(InvalidFarmException.class);
    }

    @Test
    void rechazaCoordenadasFueraDeRango() {
        assertThatThrownBy(() -> profile("Finca", new BigDecimal("90.5"), null, null))
                .isInstanceOf(InvalidFarmException.class);
        assertThatThrownBy(() -> profile("Finca", null, new BigDecimal("-180.1"), null))
                .isInstanceOf(InvalidFarmException.class);
    }

    @Test
    void aceptaUnPerfilMinimoYValoresLimite() {
        assertThatNoException().isThrownBy(() -> profile("Finca", null, null, null));
        assertThatNoException().isThrownBy(() -> profile("Finca", new BigDecimal("-90"), new BigDecimal("180"), BigDecimal.ZERO));
    }

    private static FarmProfile profile(String legalName, BigDecimal lat, BigDecimal lon, BigDecimal hectares) {
        return new FarmProfile(legalName, null, null, null, null, null, null, lat, lon, hectares,
                null, null, null, null, null, null);
    }
}
