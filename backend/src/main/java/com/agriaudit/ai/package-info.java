/**
 * Módulo {@code ai}: asistentes que extraen, clasifican y redactan <b>propuestas</b> que una persona
 * revisa (RT-A01, RT-A02). La IA propone, la persona dispone, y cada propuesta queda trazada.
 *
 * <p>Hexagonal estricto: {@code domain} y {@code port} no dependen de framework, persistencia ni SDK de
 * IA; solo {@code infrastructure} habla con proveedores (lo comprueba {@code ArchitectureTests}).
 *
 * <ul>
 *   <li>{@code port.DocExtractionPort}: extracción de texto; hoy OCR autoalojado (RapidOCR).</li>
 *   <li>{@code application.EvidenceAnalysisService}: análisis de evidencia con heurísticas deterministas;
 *       no guarda nada, solo propone.</li>
 * </ul>
 */
@ApplicationModule(displayName = "Asistentes de IA")
package com.agriaudit.ai;

import org.springframework.modulith.ApplicationModule;
