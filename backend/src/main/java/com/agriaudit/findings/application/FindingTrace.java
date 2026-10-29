package com.agriaudit.findings.application;

import com.agriaudit.findings.domain.CorrectiveAction;
import com.agriaudit.findings.domain.CorrectiveActionStatus;
import com.agriaudit.findings.domain.Finding;
import com.agriaudit.findings.domain.FindingClassification;
import com.agriaudit.findings.domain.FindingStatus;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Anota en la traza lo que ocurre con las no conformidades y sus acciones correctivas. */
@Component
class FindingTrace {

    record FindingSnap(FindingClassification classification, String title, String description, String area,
                       LocalDate dueDate, FindingStatus status) {
    }

    record CaSnap(String rootCause, String correction, String preventiveAction, String responsiblePerson,
                  LocalDate targetDate, LocalDate completedDate, String verificationNotes, String verifiedBy,
                  CorrectiveActionStatus status) {
    }

    private final ChangeRecorder trace;

    FindingTrace(ChangeRecorder trace) {
        this.trace = trace;
    }

    static FindingSnap snapshot(Finding f) {
        return new FindingSnap(f.getClassification(), f.getTitle(), f.getDescription(), f.getArea(), f.getDueDate(),
                f.getStatus());
    }

    static CaSnap snapshot(CorrectiveAction a) {
        return new CaSnap(a.getRootCause(), a.getCorrection(), a.getPreventiveAction(), a.getResponsiblePerson(),
                a.getTargetDate(), a.getCompletedDate(), a.getVerificationNotes(), a.getVerifiedBy(), a.getStatus());
    }

    void created(Finding f) {
        FindingSnap s = snapshot(f);
        Changes c = Changes.create()
                .initial("classification", "Clasificación", classification(s.classification()))
                .initial("title", "Título", s.title())
                .initial("description", "Descripción", s.description())
                .initial("area", "Área", s.area())
                .initial("dueDate", "Fecha límite", s.dueDate())
                .initial("status", "Estado", status(s.status()));
        trace.record(ChangeEntry.of(TraceEntity.FINDING, f.getId(), f.getReference(), f.getAuditId(),
                TraceAction.CREATE, "Creó la no conformidad " + f.getReference() + " «" + f.getTitle() + "»",
                c.list()));
    }

    void updated(FindingSnap before, Finding f) {
        FindingSnap after = snapshot(f);
        Changes c = Changes.create()
                .track("classification", "Clasificación", classification(before.classification()),
                        classification(after.classification()))
                .track("title", "Título", before.title(), after.title())
                .track("description", "Descripción", before.description(), after.description())
                .track("area", "Área", before.area(), after.area())
                .track("dueDate", "Fecha límite", before.dueDate(), after.dueDate())
                .track("status", "Estado", status(before.status()), status(after.status()));
        if (c.isEmpty()) {
            return;
        }
        String summary = before.status() != after.status()
                ? "Cambió el estado de la no conformidad " + f.getReference() + " de «" + status(before.status())
                + "» a «" + status(after.status()) + "»"
                : "Modificó la no conformidad " + f.getReference();
        trace.record(ChangeEntry.of(TraceEntity.FINDING, f.getId(), f.getReference(), f.getAuditId(),
                TraceAction.UPDATE, summary, c.list()));
    }

    /** Cambio de estado de la NC provocado por sus acciones correctivas (cierre automático, reapertura…). */
    void statusFollowedActions(Finding f, FindingStatus before) {
        trace.record(ChangeEntry.of(TraceEntity.FINDING, f.getId(), f.getReference(), f.getAuditId(),
                TraceAction.UPDATE, "La no conformidad " + f.getReference() + " pasó de «" + status(before)
                        + "» a «" + status(f.getStatus()) + "» según el estado de sus acciones correctivas",
                Changes.create().track("status", "Estado", status(before), status(f.getStatus())).list()));
    }

    void deleted(Finding f) {
        trace.record(ChangeEntry.of(TraceEntity.FINDING, f.getId(), f.getReference(), f.getAuditId(),
                TraceAction.DELETE, "Eliminó la no conformidad " + f.getReference() + " «" + f.getTitle() + "»",
                Changes.create().track("title", "Título", f.getTitle(), null)
                        .track("status", "Estado", status(f.getStatus()), null).list()));
    }

    void actionCreated(Finding f, CorrectiveAction a) {
        CaSnap s = snapshot(a);
        Changes c = Changes.create()
                .initial("rootCause", "Causa raíz", s.rootCause())
                .initial("correction", "Corrección", s.correction())
                .initial("preventiveAction", "Acción preventiva", s.preventiveAction())
                .personalInitial("responsiblePerson", "Responsable", s.responsiblePerson())
                .initial("targetDate", "Fecha objetivo", s.targetDate())
                .initial("status", "Estado de la acción", actionStatus(s.status()));
        trace.record(child(f, a, TraceAction.CREATE,
                "Añadió una acción correctiva a la no conformidad " + f.getReference(), c));
    }

    void actionUpdated(Finding f, CaSnap before, CorrectiveAction a) {
        CaSnap after = snapshot(a);
        Changes c = Changes.create()
                .track("rootCause", "Causa raíz", before.rootCause(), after.rootCause())
                .track("correction", "Corrección", before.correction(), after.correction())
                .track("preventiveAction", "Acción preventiva", before.preventiveAction(), after.preventiveAction())
                .personal("responsiblePerson", "Responsable", before.responsiblePerson(), after.responsiblePerson())
                .track("targetDate", "Fecha objetivo", before.targetDate(), after.targetDate())
                .track("completedDate", "Fecha de finalización", before.completedDate(), after.completedDate())
                .track("verificationNotes", "Notas de verificación", before.verificationNotes(),
                        after.verificationNotes())
                .personal("verifiedBy", "Verificada por", before.verifiedBy(), after.verifiedBy())
                .track("status", "Estado de la acción", actionStatus(before.status()), actionStatus(after.status()));
        if (c.isEmpty()) {
            return;
        }
        String summary = before.status() != after.status()
                ? "Cambió la acción correctiva de la no conformidad " + f.getReference() + " de «"
                + actionStatus(before.status()) + "» a «" + actionStatus(after.status()) + "»"
                : "Modificó una acción correctiva de la no conformidad " + f.getReference();
        trace.record(child(f, a, TraceAction.UPDATE, summary, c));
    }

    void actionDeleted(Finding f, CorrectiveAction a) {
        trace.record(child(f, a, TraceAction.DELETE,
                "Eliminó una acción correctiva de la no conformidad " + f.getReference(),
                Changes.create().track("status", "Estado de la acción", actionStatus(a.getStatus()), null)));
    }

    private static ChangeEntry child(Finding f, CorrectiveAction a, TraceAction action, String summary, Changes c) {
        return new ChangeEntry(TraceEntity.CORRECTIVE_ACTION, a.getId(), f.getReference() + " · acción",
                TraceEntity.FINDING, f.getId(), f.getAuditId(), action, summary, c.list());
    }

    static String status(FindingStatus s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case OPEN -> "Abierta";
            case IN_PROGRESS -> "En curso";
            case RESOLVED -> "Resuelta";
            case VERIFIED -> "Verificada";
            case CLOSED -> "Cerrada";
        };
    }

    static String classification(FindingClassification c) {
        if (c == null) {
            return null;
        }
        return switch (c) {
            case CRITICAL -> "Crítica";
            case MAJOR -> "Mayor";
            case MINOR -> "Menor";
            case OBSERVATION -> "Observación";
        };
    }

    static String actionStatus(CorrectiveActionStatus s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case PENDING -> "Pendiente";
            case IN_PROGRESS -> "En curso";
            case COMPLETED -> "Completada";
            case VERIFIED -> "Verificada";
            case REJECTED -> "Rechazada";
        };
    }
}
