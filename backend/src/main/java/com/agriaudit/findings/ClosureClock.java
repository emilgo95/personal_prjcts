package com.agriaudit.findings;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reloj de cierre de las no conformidades (RF-301): fecha límite a partir de la fecha de la auditoría y del plazo del
 * estándar, y si una NC cerrada lo hizo a tiempo. Los días son naturales y se cuentan en hora peninsular española.
 */
public final class ClosureClock {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    /** Avisos: días que faltan para el vencimiento a partir de los cuales se avisa (T-14, T-7, T-2). */
    public static final int WARN_14 = 14;
    public static final int WARN_7 = 7;
    public static final int WARN_2 = 2;

    private ClosureClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Fecha límite: {@code days} días naturales desde la fecha de la auditoría (o desde {@code today} si aún no tiene
     * fecha). {@code null} si la gravedad no tiene plazo.
     */
    public static LocalDate dueDate(LocalDate auditDate, Integer days, LocalDate today) {
        if (days == null) {
            return null;
        }
        return (auditDate != null ? auditDate : today).plusDays(days);
    }

    /** Si una NC cerrada en {@code closedAt} lo hizo dentro de su fecha límite (el mismo día cuenta como a tiempo). */
    public static boolean closedOnTime(Instant closedAt, LocalDate dueDate) {
        return !closedAt.atZone(ZONE).toLocalDate().isAfter(dueDate);
    }
}
