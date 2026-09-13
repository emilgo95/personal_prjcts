package com.agriaudit.findings.infrastructure.demo;

import com.agriaudit.audit.AuditRef;
import com.agriaudit.audit.AuditStatus;
import com.agriaudit.audit.DemoAuditSeeded;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.findings.domain.CorrectiveAction;
import com.agriaudit.findings.domain.CorrectiveActionStatus;
import com.agriaudit.findings.domain.Finding;
import com.agriaudit.findings.domain.FindingClassification;
import com.agriaudit.findings.domain.FindingStatus;
import com.agriaudit.findings.infrastructure.persistence.CorrectiveActionRepository;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * No conformidades y acciones correctivas de demostración para cada auditoría sembrada
 * ({@link DemoAuditSeeded}): hasta 5 NC por auditoría no borrador, con estados coherentes con el de la
 * auditoría.
 */
@Component
class FindingDemoSeeder {

    private static final int MAX_FINDINGS_PER_AUDIT = 5;

    private final FindingRepository findings;
    private final CorrectiveActionRepository correctiveActions;
    private final CatalogLookup catalog;

    FindingDemoSeeder(FindingRepository findings, CorrectiveActionRepository correctiveActions, CatalogLookup catalog) {
        this.findings = findings;
        this.correctiveActions = correctiveActions;
        this.catalog = catalog;
    }

    @EventListener
    @Transactional
    public void on(DemoAuditSeeded event) {
        AuditRef audit = event.audit();
        if (audit.status() == AuditStatus.DRAFT) {
            return;
        }
        int seq = 0;
        for (Long cpId : event.nonCompliantControlPointIds()) {
            if (seq >= MAX_FINDINGS_PER_AUDIT) {
                break;
            }
            ControlPointView cp = catalog.findControlPoint(cpId).orElse(null);
            if (cp == null) {
                continue;
            }
            seq++;
            createFinding(audit, cp, seq);
        }
    }

    private void createFinding(AuditRef audit, ControlPointView cp, int seq) {
        AuditStatus status = audit.status();
        LocalDate date = audit.plannedStartDate();
        FindingClassification classification = switch (cp.level()) {
            case MAJOR_MUST, CORE -> FindingClassification.MAJOR;
            case MINOR_MUST, IMPROVEMENT_L1 -> FindingClassification.MINOR;
            default -> FindingClassification.OBSERVATION;
        };
        Finding finding = new Finding();
        finding.setAuditId(audit.id());
        finding.setControlPointId(cp.id());
        finding.setReference(String.format("NC-%03d", seq));
        finding.setClassification(classification);
        finding.setTitle("Incumplimiento en " + cp.ref());
        finding.setDescription("Durante la auditoría no se evidenció el cumplimiento del criterio \""
                + cp.title() + "\". Debe subsanarse y aportarse evidencia objetiva.");
        finding.setArea(cp.chapterTitle());
        finding.setDueDate(date.plusDays(28));
        finding.setStatus(switch (status) {
            case CLOSED -> FindingStatus.CLOSED;
            case SUBMITTED -> FindingStatus.RESOLVED;
            case CLOSING -> (seq % 2 == 0) ? FindingStatus.IN_PROGRESS : FindingStatus.OPEN;
            default -> FindingStatus.OPEN;
        });
        finding = findings.save(finding);

        if (status == AuditStatus.CLOSED || status == AuditStatus.SUBMITTED || status == AuditStatus.CLOSING
                || (status == AuditStatus.IN_PROGRESS && seq % 2 == 1)) {
            createCorrectiveAction(finding, cp, status, date);
        }
    }

    private void createCorrectiveAction(Finding finding, ControlPointView cp, AuditStatus status, LocalDate date) {
        CorrectiveAction ca = new CorrectiveAction();
        ca.setFindingId(finding.getId());
        ca.setRootCause("Ausencia de procedimiento documentado y de responsable asignado para "
                + cp.chapterTitle().toLowerCase() + ".");
        ca.setCorrection("Se implanta el registro correspondiente y se recopila la evidencia del punto " + cp.ref() + ".");
        ca.setPreventiveAction("Se incorpora la revisión de este punto a la lista de verificación interna trimestral.");
        ca.setResponsiblePerson("Responsable de calidad");
        ca.setTargetDate(date.plusDays(21));

        switch (status) {
            case CLOSED -> {
                ca.setStatus(CorrectiveActionStatus.VERIFIED);
                ca.setCompletedDate(date.plusDays(15));
                ca.setVerificationNotes("Evidencia revisada y aceptada por el auditor en la verificación documental.");
                ca.setVerifiedBy("Ana Auditora");
            }
            case SUBMITTED -> {
                ca.setStatus(CorrectiveActionStatus.COMPLETED);
                ca.setCompletedDate(date.plusDays(18));
            }
            case CLOSING -> ca.setStatus(CorrectiveActionStatus.IN_PROGRESS);
            default -> ca.setStatus(CorrectiveActionStatus.PENDING);
        }
        correctiveActions.save(ca);
    }
}
