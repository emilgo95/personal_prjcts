package com.agriaudit.audit;

import java.time.LocalDate;

/** Datos de identificación de una auditoría que necesitan otros módulos (sin exponer la entidad). */
public record AuditRef(
        Long id,
        Long organizationId,
        String reference,
        String standardCode,
        AuditStatus status,
        LocalDate plannedStartDate,
        String leadAuditor,
        LocalDate actualDate) {

    /** Fecha en que se hizo la auditoría (la real o, si aún no consta, la prevista); de ella cuenta el plazo de las NC. */
    public LocalDate auditDate() {
        return actualDate != null ? actualDate : plannedStartDate;
    }
}
