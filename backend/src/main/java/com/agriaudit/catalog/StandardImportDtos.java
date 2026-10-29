package com.agriaudit.catalog;

import java.util.List;
import java.util.Map;

/** Datos de la importación de un estándar propio (RF-CAT-01…05). */
public final class StandardImportDtos {

    private StandardImportDtos() {
    }

    /**
     * Datos del estándar que se importa. {@code scoringType}: MAJOR_MINOR, LEVEL_COMPLETION, PILLAR o REPORT_ONLY.
     * Los porcentajes mínimos solo se usan con MAJOR_MINOR.
     */
    public record ImportMetadata(String code, String name, String version, String ownerBody, String category,
                                 String description, String officialUrl, String scoringType,
                                 Double majorMustMinPct, Double minorMustMinPct) {
    }

    /** Un problema de la importación: fila del fichero (0 = datos del estándar), columna y mensaje. */
    public record ImportIssue(int row, String column, String message) {
    }

    public record PreviewPoint(int row, String chapterCode, String ref, String title, String level) {
    }

    /** Resultado de validar un fichero sin guardar nada. */
    public record ImportPreview(boolean valid, String storedCode, int chapterCount, int pointCount,
                                Map<String, Integer> levelCounts, List<ImportIssue> issues, int totalIssues,
                                List<PreviewPoint> sample) {
    }
}
