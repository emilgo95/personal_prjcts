package com.agriaudit.catalog;

/**
 * Punto de control tal como lo ven los demás módulos, con los datos de su capítulo y su estándar.
 */
public record ControlPointView(
        Long id,
        String ref,
        String title,
        String description,
        String guidance,
        ControlLevel level,
        String applicableScopes,
        String chapterCode,
        String chapterTitle,
        Long standardId,
        String standardCode) {

    public boolean belongsTo(Long standardId) {
        return this.standardId != null && this.standardId.equals(standardId);
    }
}
