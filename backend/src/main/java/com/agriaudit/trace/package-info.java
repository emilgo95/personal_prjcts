/**
 * Módulo {@code trace}: traza de cambios inalterable (RF-TR-01…RF-TR-10).
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.trace.ChangeRecorder} (los demás módulos anotan
 *       aquí lo que cambia, dentro de su misma transacción), {@link com.agriaudit.trace.ChangeEntry},
 *       {@link com.agriaudit.trace.Changes}, {@link com.agriaudit.trace.TraceDtos}, y los puertos que implementan
 *       otros módulos: {@link com.agriaudit.trace.ActorProvider} ({@code identity}) y
 *       {@link com.agriaudit.trace.TraceAudience} ({@code audit}).</li>
 *   <li>{@code application}, {@code infrastructure}: internos (cadena de huellas, verificación, sellos, consulta).</li>
 * </ul>
 *
 * <p>No depende de ningún otro módulo: quién actúa y qué auditorías ve cada auditora se lo dan los puertos, de modo
 * que la dependencia va siempre hacia {@code trace} y no hay ciclos.
 */
@ApplicationModule(displayName = "Trazabilidad")
package com.agriaudit.trace;

import org.springframework.modulith.ApplicationModule;
