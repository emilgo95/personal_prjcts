package com.agriaudit.findings.application;

import com.agriaudit.audit.FindingsCounter;
import com.agriaudit.common.BadRequestException;
import com.agriaudit.findings.ClosureClock;
import com.agriaudit.findings.domain.CorrectiveActionStatus;
import com.agriaudit.findings.domain.Finding;
import com.agriaudit.findings.domain.FindingStatus;
import com.agriaudit.findings.infrastructure.persistence.CorrectiveActionRepository;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementación de {@link FindingsCounter} que declara {@code audit}. Bean propio, sin depender de
 * {@code FindingService}: este usa {@code AuditLookup}, y así no hay ciclo de beans entre los módulos.
 */
@Component
class AuditFindingsCounter implements FindingsCounter {

    /** Longitud máxima de la justificación de un retraso. */
    static final int MAX_JUSTIFICATION = 2000;

    private final FindingRepository findings;
    private final CorrectiveActionRepository correctiveActions;
    private final FindingTrace trace;

    AuditFindingsCounter(FindingRepository findings, CorrectiveActionRepository correctiveActions,
                         FindingTrace trace) {
        this.findings = findings;
        this.correctiveActions = correctiveActions;
        this.trace = trace;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByAudit(Long auditId) {
        return findings.countByAuditId(auditId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> countByAudits(java.util.Collection<Long> auditIds) {
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        if (!auditIds.isEmpty()) {
            for (Object[] row : findings.countByAuditIds(auditIds)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<Long> controlPointIdsWithFinding(Long auditId) {
        return new java.util.HashSet<>(findings.findControlPointIdsByAuditId(auditId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenByAudit(Long auditId) {
        return findings.countByAuditIdAndStatusNotIn(auditId, List.of(FindingStatus.CLOSED, FindingStatus.VERIFIED));
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionCheck submissionCheck(Long auditId) {
        LocalDate today = ClosureClock.today();
        List<PendingFinding> toReview = new ArrayList<>();
        List<PendingFinding> overdue = new ArrayList<>();
        for (Finding f : findings.findByAuditIdOrderByClassificationAscIdAsc(auditId)) {
            if (isClosed(f)) {
                continue;
            }
            if (awaitsReview(f)) {
                toReview.add(pending(f));
            } else if (isOverdue(f, today)) {
                overdue.add(pending(f));
            }
        }
        return new SubmissionCheck(toReview, overdue);
    }

    @Override
    @Transactional
    public void justifyLate(Long auditId, Map<Long, String> justifications) {
        LocalDate today = ClosureClock.today();
        for (Map.Entry<Long, String> e : justifications.entrySet()) {
            Finding f = findings.findById(e.getKey())
                    .filter(x -> x.getAuditId().equals(auditId))
                    .orElseThrow(() -> new BadRequestException("La no conformidad " + e.getKey()
                            + " no es de esta auditoría"));
            if (isClosed(f) || awaitsReview(f) || !isOverdue(f, today)) {
                throw new BadRequestException("La no conformidad " + f.getReference()
                        + " no está vencida y sin cerrar: no necesita justificación");
            }
            String text = e.getValue() == null ? "" : e.getValue().trim();
            if (text.isEmpty()) {
                throw new BadRequestException("Indica el motivo del retraso de " + f.getReference());
            }
            if (text.length() > MAX_JUSTIFICATION) {
                throw new BadRequestException("La justificación de " + f.getReference() + " supera "
                        + MAX_JUSTIFICATION + " caracteres");
            }
            if (text.equals(f.getLateJustification())) {
                continue;
            }
            String before = f.getLateJustification();
            f.justifyLate(text, Instant.now());
            findings.save(f);
            trace.lateJustified(f, before);
        }
    }

    private static boolean isClosed(Finding f) {
        return f.getStatus() == FindingStatus.CLOSED || f.getStatus() == FindingStatus.VERIFIED;
    }

    private static boolean isOverdue(Finding f, LocalDate today) {
        return f.getDueDate() != null && f.getDueDate().isBefore(today);
    }

    /** Hecha y pendiente de verificar: marcada como resuelta o con alguna acción correctiva completada. */
    private boolean awaitsReview(Finding f) {
        return f.getStatus() == FindingStatus.RESOLVED
                || correctiveActions.findByFindingIdOrderByIdAsc(f.getId()).stream()
                        .anyMatch(a -> a.getStatus() == CorrectiveActionStatus.COMPLETED);
    }

    private static PendingFinding pending(Finding f) {
        return new PendingFinding(f.getId(), f.getReference(), f.getTitle(), f.getDueDate(), f.getLateJustification());
    }
}
