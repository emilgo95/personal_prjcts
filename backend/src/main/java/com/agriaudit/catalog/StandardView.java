package com.agriaudit.catalog;

/**
 * Estándar de auditoría tal como lo ven los demás módulos.
 *
 * @param scoringRule regla de puntuación en JSON (puede ser {@code null})
 */
public record StandardView(Long id, String code, String version, String name, String scoringRule) {
}
