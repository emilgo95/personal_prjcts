package com.agriaudit.audit;

import com.agriaudit.audit.AuditDtos.AuditDetailDto;
import com.agriaudit.audit.AuditDtos.AuditItemResultDto;
import com.agriaudit.audit.AuditDtos.AuditSummaryDto;
import com.agriaudit.audit.ScoreDtos.ScoreResult;

import java.util.List;

/**
 * Auditorías vistas desde otros módulos (API pública de {@code audit}).
 *
 * <p>Los métodos sin organización explícita actúan sobre la organización del usuario autenticado y
 * lanzan {@code NotFoundException} si la auditoría no existe o es de otra organización.
 */
public interface AuditLookup {

    AuditRef requireAudit(Long auditId);

    AuditDetailDto get(Long auditId);

    List<AuditSummaryDto> list();

    /** Checklist completo de la auditoría, con resultado y adjuntos por punto de control. */
    List<AuditItemResultDto> getResults(Long auditId);

    ScoreResult score(Long auditId);

    /** Auditorías de una organización, sin usuario en contexto (procesos internos y datos de demostración). */
    List<AuditRef> findByOrganization(Long organizationId);
}
