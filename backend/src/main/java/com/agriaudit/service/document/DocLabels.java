package com.agriaudit.service.document;

/** Traducciones al español de los códigos de enumeración para los documentos. */
public final class DocLabels {

    private DocLabels() {
    }

    public static String compliance(String value) {
        if (value == null) return "";
        return switch (value) {
            case "COMPLIANT" -> "Conforme";
            case "NON_COMPLIANT" -> "No conforme";
            case "NOT_APPLICABLE" -> "No aplica";
            case "NOT_ASSESSED" -> "Sin evaluar";
            default -> value;
        };
    }

    public static String level(String value) {
        if (value == null) return "";
        return switch (value) {
            case "MAJOR_MUST" -> "Obligación mayor";
            case "MINOR_MUST" -> "Obligación menor";
            case "RECOMMENDATION" -> "Recomendación";
            case "CORE" -> "Requisito esencial";
            case "IMPROVEMENT_L1" -> "Mejora nivel 1";
            case "IMPROVEMENT_L2" -> "Mejora nivel 2";
            default -> value;
        };
    }

    public static String classification(String value) {
        if (value == null) return "";
        return switch (value) {
            case "CRITICAL" -> "Crítica";
            case "MAJOR" -> "Mayor";
            case "MINOR" -> "Menor";
            case "OBSERVATION" -> "Observación";
            default -> value;
        };
    }

    public static String findingStatus(String value) {
        if (value == null) return "";
        return switch (value) {
            case "OPEN" -> "Abierta";
            case "IN_PROGRESS" -> "En curso";
            case "RESOLVED" -> "Resuelta";
            case "VERIFIED" -> "Verificada";
            case "CLOSED" -> "Cerrada";
            default -> value;
        };
    }

    public static String caStatus(String value) {
        if (value == null) return "";
        return switch (value) {
            case "PENDING" -> "Pendiente";
            case "IN_PROGRESS" -> "En curso";
            case "COMPLETED" -> "Completada";
            case "VERIFIED" -> "Verificada";
            case "REJECTED" -> "Rechazada";
            default -> value;
        };
    }

    public static String auditType(String value) {
        if (value == null) return "";
        return switch (value) {
            case "SELF_ASSESSMENT" -> "Autoevaluación";
            case "INTERNAL" -> "Auditoría interna";
            case "CB_INITIAL" -> "Certificación inicial (organismo)";
            case "SURVEILLANCE" -> "Seguimiento";
            case "RECERTIFICATION" -> "Recertificación";
            default -> value;
        };
    }

    public static String auditStatus(String value) {
        if (value == null) return "";
        return switch (value) {
            case "DRAFT" -> "Borrador";
            case "IN_PROGRESS" -> "En curso";
            case "CLOSING" -> "Cierre";
            case "SUBMITTED" -> "Enviada";
            case "CLOSED" -> "Cerrada";
            default -> value;
        };
    }

    public static String overallResult(String value) {
        if (value == null) return "";
        return switch (value) {
            case "PASS" -> "APTO";
            case "FAIL" -> "NO APTO";
            case "PENDING" -> "PENDIENTE";
            case "REPORT_ONLY" -> "INFORME (sin resultado apto/no apto)";
            default -> value;
        };
    }
}
