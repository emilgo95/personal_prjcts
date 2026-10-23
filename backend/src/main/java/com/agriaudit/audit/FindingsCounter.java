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
}
