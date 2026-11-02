package com.agriaudit.documents.infrastructure.render;

import com.agriaudit.findings.FindingDtos.FindingDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocLabelsTest {

    private static FindingDto finding(LocalDate due, String justification) {
        return new FindingDto(1L, 7L, 102L, "AF 2.5", "Autoevaluación", "NC-001", "MINOR", "Título", null, null,
                due, "OPEN", Instant.now(), List.of(), List.of(), null, justification);
    }

    @Test
    void theDueDateCellCarriesTheLateJustificationWhenThereIsOne() {
        assertThat(DocLabels.dueWithJustification(finding(LocalDate.of(2026, 9, 15), null))).isEqualTo("2026-09-15");
        assertThat(DocLabels.dueWithJustification(finding(null, null))).isEqualTo("-");
        assertThat(DocLabels.dueWithJustification(finding(LocalDate.of(2026, 9, 15), "Pendiente del laboratorio")))
                .isEqualTo("2026-09-15 · Retraso justificado: Pendiente del laboratorio");
    }
}
