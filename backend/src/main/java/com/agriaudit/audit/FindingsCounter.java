package com.agriaudit.audit;

/**
 * Lo que {@code audit} necesita saber de las no conformidades para sus resúmenes. Lo declara
 * {@code audit} y lo implementa {@code findings}: así la dependencia va solo {@code findings → audit}
 * y no hay ciclo entre los dos módulos (inversión de dependencias).
 */
public interface FindingsCounter {

    long countByAudit(Long auditId);

    /** Nº de NC de cada auditoría indicada (las que no tienen ninguna no aparecen). */
    java.util.Map<Long, Long> countByAudits(java.util.Collection<Long> auditIds);

    /** Puntos de control de la auditoría que tienen al menos una no conformidad registrada. */
    java.util.Set<Long> controlPointIdsWithFinding(Long auditId);

    /** NC de la auditoría que no están cerradas ni verificadas. */
    long countOpenByAudit(Long auditId);

    /** Una no conformidad que impide o condiciona el envío de la auditoría. */
    record PendingFinding(Long id, String reference, String title, java.time.LocalDate dueDate,
                          String lateJustification) {
    }

    /**
     * Lo que hay que resolver antes de enviar: {@code toReview}, NC sin cerrar con su acción correctiva ya
     * completada (o marcadas como resueltas) que falta verificar, y {@code overdue}, el resto de NC sin cerrar
     * cuyo plazo ya venció (con el mismo «hoy» que el reloj de cierre y el panel).
     */
    record SubmissionCheck(java.util.List<PendingFinding> toReview, java.util.List<PendingFinding> overdue) {
    }

    SubmissionCheck submissionCheck(Long auditId);

    /**
     * Guarda la justificación del retraso de NC vencidas de la auditoría (id → texto) y lo deja en la traza.
     * Rechaza las NC que no estén vencidas y sin cerrar, y los textos vacíos.
     */
    void justifyLate(Long auditId, java.util.Map<Long, String> justifications);
}
