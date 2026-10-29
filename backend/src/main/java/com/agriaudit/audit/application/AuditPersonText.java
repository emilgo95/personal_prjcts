package com.agriaudit.audit.application;

import java.util.regex.Pattern;

/** Búsqueda y sustitución de un nombre dentro de un texto libre (p. ej. el equipo auditor) respetando palabras completas. */
final class AuditPersonText {

    private static final Pattern NOT_ALNUM = Pattern.compile("([^\\p{L}\\p{N}\\s])");

    private AuditPersonText() {
    }

    /** Expresión regular de PostgreSQL ({@code \\y} = frontera de palabra) que casa con cualquiera de los valores como palabra completa. */
    static String wordPattern(Object[] keys) {
        StringBuilder sb = new StringBuilder();
        for (Object key : keys) {
            String k = key == null ? "" : key.toString().trim();
            if (k.length() < 3) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(NOT_ALNUM.matcher(k).replaceAll("\\\\$1"));
        }
        return sb.length() == 0 ? "(?!)" : "\\y(" + sb + ")\\y";
    }
}
