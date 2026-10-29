package com.agriaudit.catalog;

/**
 * Plazo de cierre de las no conformidades de un estándar, en días naturales desde la fecha de la auditoría, por
 * gravedad (RF-308). {@code null} = sin plazo automático para esa gravedad.
 */
public record ClosureRule(Integer criticalDays, Integer majorDays, Integer minorDays, Integer observationDays) {

    /** Regla de GLOBALG.A.P.: 28 días para críticas, mayores y menores; las observaciones no tienen plazo. */
    public static final ClosureRule DEFAULT = new ClosureRule(28, 28, 28, null);

    /** Días de plazo para una gravedad ({@code CRITICAL}, {@code MAJOR}, {@code MINOR}, {@code OBSERVATION}). */
    public Integer daysFor(String classification) {
        if (classification == null) {
            return null;
        }
        return switch (classification) {
            case "CRITICAL" -> criticalDays;
            case "MAJOR" -> majorDays;
            case "MINOR" -> minorDays;
            case "OBSERVATION" -> observationDays;
            default -> null;
        };
    }
}
