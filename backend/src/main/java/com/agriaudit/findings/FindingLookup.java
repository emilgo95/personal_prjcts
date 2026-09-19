package com.agriaudit.findings;

import com.agriaudit.findings.FindingDtos.FindingDto;

import java.time.LocalDate;
import java.util.List;

/** No conformidades vistas desde otros módulos (API pública de {@code findings}). */
public interface FindingLookup {

    /** NC de una auditoría de la organización del usuario, con acciones correctivas y adjuntos. */
    List<FindingDto> listByAudit(Long auditId);

    /** NC no cerradas ni verificadas de la organización. */
    long countOpen(Long organizationId);

    /** NC no cerradas ni verificadas cuya fecha límite es anterior a {@code today}. */
    long countOverdue(Long organizationId, LocalDate today);

    /** NC no cerradas ni verificadas con fecha límite entre {@code from} y {@code to}, ambas incluidas. */
    long countDueBetween(Long organizationId, LocalDate from, LocalDate to);

    /**
     * Plazos de las NC no cerradas ni verificadas con fecha límite, de la más urgente (vencida) a la menos,
     * hasta {@code limit}.
     */
    List<FindingDeadline> nextDeadlines(Long organizationId, int limit);

    /** Una NC abierta con su fecha límite, para el panel de vencimientos. */
    record FindingDeadline(Long findingId, Long auditId, String reference, String title, String classification,
                           String status, LocalDate dueDate) {
    }
}
