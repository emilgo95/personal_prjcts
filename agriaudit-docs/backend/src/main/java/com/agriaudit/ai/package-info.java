/**
 * Módulo {@code ai}: asistentes que extraen, clasifican y redactan <b>propuestas</b> que una persona
 * revisa (RT-A01, RT-A02). La IA propone, la persona dispone, y cada propuesta queda trazada.
 *
 * <p>Hexagonal estricto: {@code domain} y {@code port} no dependen de framework, persistencia ni SDK de
 * IA; solo {@code infrastructure.anthropic} conoce el SDK de Anthropic (lo comprueba {@code ArchitectureTests}).
 *
 * <ul>
 *   <li>{@code port.LlmPort}: modelo de lenguaje; adaptadores {@code none} (por defecto), {@code fake}
 *       (tests/CI) y {@code anthropic} (Claude API), elegidos con {@code agriaudit.ai.provider}.</li>
 *   <li>{@code domain.AiProposal} + {@code application.AiProposalService}: patrón «propuesta» con traza de
 *       modelo, prompt, entradas, tokens, coste y revisión humana.</li>
 *   <li>{@code port.PromptRegistry}: prompts versionados en {@code resources/ai/prompts} (RT-A10).</li>
 *   <li>{@code domain.LlmRequest}: el contenido no confiable viaja delimitado como dato (RS-IA-03).</li>
 *   <li>{@code port.DocExtractionPort}: extracción de texto; hoy OCR autoalojado (RapidOCR).</li>
 * </ul>
 */
@ApplicationModule(displayName = "Asistentes de IA")
package com.agriaudit.ai;

import org.springframework.modulith.ApplicationModule;
