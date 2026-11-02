package com.agriaudit.ai.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceNegationTest {

    @Test
    void sinHallazgosNoCuentaComoIncumplimiento() {
        String n = EvidenceAnalysisService.neutralizeNegatedFindings(
                "informe de revision: sin hallazgos; no se han detectado incumplimientos ni desviaciones.");

        assertThat(n).doesNotContain("hallazgo").doesNotContain("incumpl").contains("sin incidencias");
    }

    @Test
    void unHallazgoRealSeConserva() {
        String n = EvidenceAnalysisService.neutralizeNegatedFindings("se registra un hallazgo en el almacen");

        assertThat(n).contains("hallazgo");
    }
}
