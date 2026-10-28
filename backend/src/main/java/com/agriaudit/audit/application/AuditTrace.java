package com.agriaudit.audit.application;

import com.agriaudit.audit.AuditStatus;
import com.agriaudit.audit.domain.Audit;
import com.agriaudit.audit.domain.ComplianceResult;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Anota en la traza lo que ocurre con las auditorías y con los resultados de su checklist. */
@Component
class AuditTrace {

    record AuditSnap(Long farmId, String auditType, String scope, LocalDate plannedStart, LocalDate plannedEnd,
                     LocalDate actualDate, String leadAuditor, String team, String siteAddress, String products) {
    }

    record ResultSnap(ComplianceResult compliance, String evidenceNotes, String auditorComment) {
    }

    private final ChangeRecorder trace;

    AuditTrace(ChangeRecorder trace) {
        this.trace = trace;
    }

    static AuditSnap snapshot(Audit a) {
        return new AuditSnap(a.getFarmId(), a.getAuditType() == null ? null : a.getAuditType().name(),
                a.getScopeDescription(), a.getPlannedStartDate(), a.getPlannedEndDate(), a.getActualDate(),
                a.getLeadAuditor(), a.getAuditTeam(), a.getSiteAddress(), a.getProductsInScope());
    }

    void created(Audit a, String farmName, String standardName) {
        AuditSnap s = snapshot(a);
        Changes c = Changes.create()
                .initial("farm", "Explotación", farmName)
                .initial("standard", "Estándar", standardName)
                .initial("auditType", "Tipo", s.auditType())
                .initial("scope", "Alcance", s.scope())
                .initial("plannedStart", "Inicio previsto", s.plannedStart())
                .initial("plannedEnd", "Fin previsto", s.plannedEnd())
                .initial("leadAuditor", "Auditor líder", s.leadAuditor())
                .initial("team", "Equipo auditor", s.team());
        trace.record(ChangeEntry.of(TraceEntity.AUDIT, a.getId(), a.getReference(), a.getId(), TraceAction.CREATE,
                "Creó la auditoría " + a.getReference() + (farmName == null ? "" : " de " + farmName), c.list()));
    }

    void updated(AuditSnap before, Audit a) {
        AuditSnap after = snapshot(a);
        Changes c = Changes.create()
                .track("farmId", "Explotación (id)", before.farmId(), after.farmId())
                .track("auditType", "Tipo", before.auditType(), after.auditType())
                .track("scope", "Alcance", before.scope(), after.scope())
                .track("plannedStart", "Inicio previsto", before.plannedStart(), after.plannedStart())
                .track("plannedEnd", "Fin previsto", before.plannedEnd(), after.plannedEnd())
                .track("actualDate", "Fecha real", before.actualDate(), after.actualDate())
                .track("leadAuditor", "Auditor líder", before.leadAuditor(), after.leadAuditor())
                .track("team", "Equipo auditor", before.team(), after.team())
                .track("siteAddress", "Dirección del centro", before.siteAddress(), after.siteAddress())
                .track("products", "Productos en alcance", before.products(), after.products());
        if (c.isEmpty()) {
            return;
        }
        trace.record(ChangeEntry.of(TraceEntity.AUDIT, a.getId(), a.getReference(), a.getId(), TraceAction.UPDATE,
                "Modificó los datos de la auditoría " + a.getReference(), c.list()));
    }

    void transitioned(Audit a, AuditStatus from, AuditStatus to) {
        trace.record(ChangeEntry.of(TraceEntity.AUDIT, a.getId(), a.getReference(), a.getId(),
                TraceAction.TRANSITION, "Cambió el estado de la auditoría " + a.getReference() + " de «"
                        + AuditService.stateLabel(from) + "» a «" + AuditService.stateLabel(to) + "»",
                Changes.create().track("status", "Estado", AuditService.stateLabel(from),
                        AuditService.stateLabel(to)).list()));
    }

    /** Un punto de control del checklist cambió (o se evaluó por primera vez). */
    void resultChanged(Audit a, String pointRef, ResultSnap before, ResultSnap after) {
        Changes c = Changes.create()
                .track("compliance", "Resultado", complianceLabel(before == null ? null : before.compliance()),
                        complianceLabel(after.compliance()))
                .track("evidenceNotes", "Notas de evidencia", before == null ? null : before.evidenceNotes(),
                        after.evidenceNotes())
                .track("auditorComment", "Comentario del auditor", before == null ? null : before.auditorComment(),
                        after.auditorComment());
        if (c.isEmpty()) {
            return;
        }
        boolean complianceChanged = before == null || before.compliance() != after.compliance();
        String summary = complianceChanged
                ? "Marcó el punto " + pointRef + " como «" + complianceLabel(after.compliance()) + "»"
                : "Modificó las notas del punto " + pointRef;
        trace.record(ChangeEntry.of(TraceEntity.AUDIT_RESULT, null, pointRef, a.getId(), TraceAction.UPDATE,
                summary, c.list()));
    }

    static String complianceLabel(ComplianceResult c) {
        if (c == null) {
            return null;
        }
        return switch (c) {
            case COMPLIANT -> "Conforme";
            case NON_COMPLIANT -> "No conforme";
            case NOT_APPLICABLE -> "No aplica";
            case NOT_ASSESSED -> "Sin evaluar";
        };
    }
}
