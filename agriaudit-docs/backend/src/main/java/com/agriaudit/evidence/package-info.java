/**
 * Módulo {@code evidence}: registros de evidencia reutilizables (análisis de agua, evaluaciones de riesgos,
 * calibraciones…) que se dan de alta una vez por explotación y sirven para varias auditorías y normas
 * (objetivo O4, RF-401 a RF-406).
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.evidence.EvidenceDtos}.</li>
 *   <li>La <b>matriz de equivalencias</b> dice qué tipo de evidencia sirve para qué punto de qué norma. La que se
 *       entrega es un <b>borrador</b> deducido de las «evidencias de ejemplo» de cada punto; cada organización la
 *       recibe como copia propia, la valida y la corrige.</li>
 *   <li>El checklist <b>sugiere</b> («cubierto por…») y una persona confirma el vínculo; nada se marca conforme solo.</li>
 *   <li>Depende de {@code audit}, {@code catalog}, {@code fieldbook}, {@code storage}, {@code trace} y
 *       {@code identity}; ninguno depende de él.</li>
 * </ul>
 */
@ApplicationModule(displayName = "Evidencias reutilizables")
package com.agriaudit.evidence;

import org.springframework.modulith.ApplicationModule;
