package com.agriaudit.catalog;

/**
 * Estándar de auditoría tal como lo ven los demás módulos.
 *
 * @param scoringRule regla de puntuación en JSON (puede ser {@code null})
 * @param closureRule plazo de cierre de las no conformidades por gravedad
 */
public record StandardView(Long id, String code, String version, String name, String scoringRule,
                           ClosureRule closureRule) {

    /** Con el plazo de cierre por defecto (28 días). */
    public StandardView(Long id, String code, String version, String name, String scoringRule) {
        this(id, code, version, name, scoringRule, ClosureRule.DEFAULT);
    }
}
