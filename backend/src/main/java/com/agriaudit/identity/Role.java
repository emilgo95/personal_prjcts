package com.agriaudit.identity;

public enum Role {
    ADMIN,
    AUDITOR,
    /** Responsable de calidad: lectura total, verifica no conformidades y envía/cierra auditorías. */
    QUALITY,
    VIEWER
}
