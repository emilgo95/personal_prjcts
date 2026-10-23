package com.agriaudit.audit;

public enum AuditStatus {
    DRAFT,
    IN_PROGRESS,
    CLOSING,
    SUBMITTED,
    CLOSED;

    /**
     * Una auditoría enviada o cerrada está congelada: no admite cambios en el checklist, las no
     * conformidades, sus acciones correctivas ni las evidencias. Para modificar una enviada hay que
     * reabrirla (pasarla a «Cierre»); una cerrada es definitiva.
     */
    public boolean isLocked() {
        return this == SUBMITTED || this == CLOSED;
    }

    /** Mensaje para el usuario cuando intenta modificar una auditoría congelada. */
    public String lockedMessage() {
        return switch (this) {
            case SUBMITTED -> "La auditoría está enviada y no admite cambios. Reábrela (pasar a «Cierre») para modificarla.";
            case CLOSED -> "La auditoría está cerrada y ya no admite cambios.";
            default -> throw new IllegalStateException("La auditoría no está congelada: " + this);
        };
    }
}
