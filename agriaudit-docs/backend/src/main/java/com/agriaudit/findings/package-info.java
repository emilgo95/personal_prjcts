/**
 * Módulo {@code findings}: no conformidades, acciones correctivas (CAPA) y sus adjuntos. Base del
 * objetivo O3 (cierre de NC en 28 días, WS-2).
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.findings.FindingLookup} y
 *       {@link com.agriaudit.findings.FindingDtos}.</li>
 *   <li>Depende de {@code audit} (para comprobar la auditoría y su organización) e implementa su
 *       interfaz requerida {@code FindingsCounter}.</li>
 *   <li>{@code domain}, {@code application}, {@code infrastructure}: internos.</li>
 * </ul>
 */
@ApplicationModule(displayName = "No conformidades")
package com.agriaudit.findings;

import org.springframework.modulith.ApplicationModule;
