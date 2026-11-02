/**
 * Módulo {@code audit}: auditorías, checklist (resultado por punto de control), puntuación por
 * estándar y adjuntos de la auditoría y de sus puntos de control.
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.audit.AuditLookup},
 *       {@link com.agriaudit.audit.AuditRef}, {@link com.agriaudit.audit.AuditStatus},
 *       {@link com.agriaudit.audit.AuditDtos}, {@link com.agriaudit.audit.ScoreDtos} y el evento
 *       {@link com.agriaudit.audit.DemoAuditSeeded}.</li>
 *   <li>Interfaz requerida {@link com.agriaudit.audit.FindingsCounter}: la implementa {@code findings}
 *       (inversión de dependencias; sin ciclo {@code audit ↔ findings}).</li>
 *   <li>{@code domain}, {@code application}, {@code infrastructure}: internos. Hexagonal estricto
 *       pendiente de WS-2/WS-3 (decisión de implementación incremental).</li>
 * </ul>
 *
 * <p>La puntuación ({@code ScoringService}) es determinista y nunca la produce un LLM (RT-A03).
 */
@ApplicationModule(displayName = "Auditorías")
package com.agriaudit.audit;

import org.springframework.modulith.ApplicationModule;
