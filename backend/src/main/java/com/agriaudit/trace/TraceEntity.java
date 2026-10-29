package com.agriaudit.trace;

/** Tipos de entidad que aparecen en la traza (valores de {@code entity_type}). */
public final class TraceEntity {

    public static final String AUDIT = "AUDIT";
    public static final String AUDIT_RESULT = "AUDIT_RESULT";
    public static final String FINDING = "FINDING";
    public static final String CORRECTIVE_ACTION = "CORRECTIVE_ACTION";
    public static final String ATTACHMENT = "ATTACHMENT";
    public static final String DOCUMENT = "DOCUMENT";
    public static final String USER = "USER";
    public static final String FARM = "FARM";
    public static final String STANDARD = "STANDARD";

    private TraceEntity() {
    }
}
