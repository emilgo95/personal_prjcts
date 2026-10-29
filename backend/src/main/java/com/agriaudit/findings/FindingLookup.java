package com.agriaudit.findings;

import com.agriaudit.findings.FindingDtos.FindingDto;

import java.time.LocalDate;
import java.util.List;

/** No conformidades vistas desde otros módulos (API pública de {@code findings}). */
public interface FindingLookup {

    /** NC de una auditoría de la organización del usuario, con acciones correctivas y adjuntos. */
    List<FindingDto> listByAudit(Long auditId);

    /**
     * Estado del reloj de cierre de la organización en una sola lectura (RF-301): NC abiertas, vencidas y que vencen
     * en 2, 7 y 14 días; NC cerradas en los últimos 12 meses con plazo y cuántas a tiempo; y los {@code limit}
     * plazos más urgentes.
     */
    DeadlineSummary deadlineSummary(Long organizationId, LocalDate today, int limit);

    /**
     * Resumen del reloj. Los recuentos «vencen en N días» son acumulados e incluyen las que vencen hoy;
     * {@code closedOnTime} de {@code closedWithDeadline} es el indicador de NC cerradas en plazo.
     */
    record DeadlineSummary(long open, long overdue, long dueIn2, long dueIn7, long dueIn14,
                           long closedWithDeadline, long closedOnTime, List<FindingDeadline> next) {
    }

    /** Una NC abierta con su fecha límite, para el panel de vencimientos. */
    record FindingDeadline(Long findingId, Long auditId, String reference, String title, String classification,
                           String status, LocalDate dueDate) {
    }
}
