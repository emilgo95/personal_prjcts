package com.agriaudit.audit;

/**
 * Lo que {@code audit} necesita saber de las no conformidades para sus resúmenes. Lo declara
 * {@code audit} y lo implementa {@code findings}: así la dependencia va solo {@code findings → audit}
 * y no hay ciclo entre los dos módulos (inversión de dependencias).
 */
public interface FindingsCounter {

    long countByAudit(Long auditId);
}
