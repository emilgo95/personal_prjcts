package com.agriaudit.findings.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.ConflictException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.catalog.ControlPointView;
import com.agriaudit.findings.domain.CorrectiveAction;
import com.agriaudit.findings.domain.Finding;
import com.agriaudit.storage.AttachmentEntityType;
import com.agriaudit.storage.AttachmentStore;
import com.agriaudit.findings.domain.CorrectiveActionStatus;
import com.agriaudit.findings.domain.FindingClassification;
import com.agriaudit.findings.domain.FindingStatus;
import com.agriaudit.catalog.CatalogLookup;
import com.agriaudit.findings.infrastructure.persistence.CorrectiveActionRepository;
import com.agriaudit.findings.infrastructure.persistence.FindingRepository;
import com.agriaudit.findings.FindingLookup;
import com.agriaudit.findings.infrastructure.persistence.FindingReferenceSequence;
import com.agriaudit.findings.FindingDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class FindingService implements FindingLookup {

    private final FindingRepository findings;
    private final CorrectiveActionRepository correctiveActions;
    private final CatalogLookup catalog;
    private final AuditLookup audits;
    private final AttachmentStore attachmentLookup;
    private final FindingReferenceSequence referenceSequences;

    public FindingService(FindingRepository findings, CorrectiveActionRepository correctiveActions,
                          CatalogLookup catalog, AuditLookup audits,
                          AttachmentStore attachmentLookup, FindingReferenceSequence referenceSequences) {
        this.findings = findings;
        this.correctiveActions = correctiveActions;
        this.catalog = catalog;
        this.audits = audits;
        this.attachmentLookup = attachmentLookup;
        this.referenceSequences = referenceSequences;
    }

    @Transactional(readOnly = true)
    public List<FindingDto> listByAudit(Long auditId) {
        audits.requireAudit(auditId);
        return findings.findByAuditIdOrderByClassificationAscIdAsc(auditId).stream().map(this::toDto).toList();
    }

    public FindingDto create(Long auditId, FindingRequest request) {
        AuditRef audit = requireEditableAudit(auditId);
        Finding finding = new Finding();
        finding.setAuditId(audit.id());
        applyControlPoint(finding, request.controlPointId(), audit);
        applyRequest(finding, request);
        validateManualStatus(finding, null, List.of());
        finding.setReference(nextReference(auditId));
        return toDto(findings.save(finding));
    }

    public FindingDto update(Long auditId, Long findingId, FindingRequest request) {
        AuditRef audit = requireEditableAudit(auditId);
        Finding finding = requireFinding(auditId, findingId);
        FindingStatus previous = finding.getStatus();
        applyControlPoint(finding, request.controlPointId(), audit);
        applyRequest(finding, request);
        validateManualStatus(finding, previous, correctiveActions.findByFindingIdOrderByIdAsc(finding.getId()));
        return toDto(findings.save(finding));
    }

    public void delete(Long auditId, Long findingId) {
        requireEditableAudit(auditId);
        findings.delete(requireFinding(auditId, findingId));
    }

    public CorrectiveActionDto addCorrectiveAction(Long auditId, Long findingId, CorrectiveActionRequest request) {
        requireEditableAudit(auditId);
        Finding finding = requireFinding(auditId, findingId);
        CorrectiveAction ca = new CorrectiveAction();
        ca.setFindingId(finding.getId());
        applyCa(ca, request);
        CorrectiveAction saved = correctiveActions.save(ca);
        syncFindingStatus(finding);
        return toDto(saved);
    }

    public CorrectiveActionDto updateCorrectiveAction(Long auditId, Long findingId, Long caId,
                                                     CorrectiveActionRequest request) {
        requireEditableAudit(auditId);
        Finding finding = requireFinding(auditId, findingId);
        CorrectiveAction ca = correctiveActions.findById(caId)
                .filter(x -> x.getFindingId().equals(finding.getId()))
                .orElseThrow(() -> new NotFoundException("Acción correctiva no encontrada: " + caId));
        applyCa(ca, request);
        CorrectiveAction saved = correctiveActions.save(ca);
        syncFindingStatus(finding);
        return toDto(saved);
    }

    public void deleteCorrectiveAction(Long auditId, Long findingId, Long caId) {
        requireEditableAudit(auditId);
        Finding finding = requireFinding(auditId, findingId);
        CorrectiveAction ca = correctiveActions.findById(caId)
                .filter(x -> x.getFindingId().equals(finding.getId()))
                .orElseThrow(() -> new NotFoundException("Acción correctiva no encontrada: " + caId));
        correctiveActions.delete(ca);
        syncFindingStatus(finding);
    }

    // ---- FindingLookup ----

    @Override
    @Transactional(readOnly = true)
    public long countOpen(Long organizationId) {
        return organizationFindings(organizationId).stream().filter(FindingService::isOpen).count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countOverdue(Long organizationId, LocalDate today) {
        return organizationFindings(organizationId).stream()
                .filter(FindingService::isOpen)
                .filter(f -> f.getDueDate() != null && f.getDueDate().isBefore(today))
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countDueBetween(Long organizationId, LocalDate from, LocalDate to) {
        return organizationFindings(organizationId).stream()
                .filter(FindingService::isOpen)
                .filter(f -> f.getDueDate() != null && !f.getDueDate().isBefore(from) && !f.getDueDate().isAfter(to))
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FindingDeadline> nextDeadlines(Long organizationId, int limit) {
        return organizationFindings(organizationId).stream()
                .filter(FindingService::isOpen)
                .filter(f -> f.getDueDate() != null)
                .sorted(java.util.Comparator.comparing(Finding::getDueDate).thenComparing(Finding::getId))
                .limit(limit)
                .map(f -> new FindingDeadline(f.getId(), f.getAuditId(), f.getReference(), f.getTitle(),
                        f.getClassification().name(), f.getStatus().name(), f.getDueDate()))
                .toList();
    }

    // ---- helpers ----

    /** Como {@link #requireOwnedFinding}, y además 409 si la auditoría está enviada o cerrada. */
    Finding requireEditableFinding(Long auditId, Long findingId) {
        requireEditableAudit(auditId);
        return requireFinding(auditId, findingId);
    }

    /** Auditoría de la organización del usuario para modificarla: 404 si no es suya, 409 si está congelada. */
    private AuditRef requireEditableAudit(Long auditId) {
        AuditRef audit = audits.requireAudit(auditId);
        if (audit.status().isLocked()) {
            throw new ConflictException(audit.status().lockedMessage());
        }
        return audit;
    }

    /**
     * Una no conformidad solo puede pasar a «cerrada» o «verificada» a mano si todas sus acciones correctivas
     * están verificadas y, salvo las observaciones, si tiene al menos una que evidencie la corrección.
     */
    private void validateManualStatus(Finding finding, FindingStatus previous, List<CorrectiveAction> actions) {
        FindingStatus target = finding.getStatus();
        if (target == previous || (target != FindingStatus.CLOSED && target != FindingStatus.VERIFIED)) {
            return;
        }
        String verb = target == FindingStatus.CLOSED ? "cerrada" : "verificada";
        if (actions.stream().anyMatch(a -> a.getStatus() != CorrectiveActionStatus.VERIFIED)) {
            throw new ConflictException("No se puede dar la no conformidad por " + verb
                    + ": tiene acciones correctivas sin verificar.");
        }
        if (actions.isEmpty() && finding.getClassification() != FindingClassification.OBSERVATION) {
            throw new ConflictException("No se puede dar la no conformidad por " + verb
                    + ": no tiene ninguna acción correctiva verificada que evidencie la corrección. "
                    + "Solo las observaciones pueden cerrarse sin acción.");
        }
    }

    /** NC de una auditoría de la organización del usuario; 404 si la auditoría o la NC no lo son. */
    Finding requireOwnedFinding(Long auditId, Long findingId) {
        audits.requireAudit(auditId);
        return requireFinding(auditId, findingId);
    }

    private List<Finding> organizationFindings(Long organizationId) {
        List<Long> auditIds = audits.findByOrganization(organizationId).stream().map(AuditRef::id).toList();
        return auditIds.isEmpty() ? List.of() : findings.findByAuditIdIn(auditIds);
    }

    private static boolean isOpen(Finding f) {
        return f.getStatus() != FindingStatus.CLOSED && f.getStatus() != FindingStatus.VERIFIED;
    }

    private Finding requireFinding(Long auditId, Long findingId) {
        return findings.findByIdAndAuditId(findingId, auditId)
                .orElseThrow(() -> new NotFoundException("Hallazgo no encontrado: " + findingId));
    }

    private void applyControlPoint(Finding finding, Long controlPointId, AuditRef audit) {
        if (controlPointId == null) {
            finding.setControlPointId(null);
            return;
        }
        ControlPointView cp = catalog.findControlPoint(controlPointId)
                .orElseThrow(() -> new NotFoundException("Punto de control no encontrado: " + controlPointId));
        if (!cp.standardCode().equals(audit.standardCode())) {
            throw new BadRequestException("El punto de control no pertenece al estándar de la auditoría");
        }
        finding.setControlPointId(cp.id());
    }

    private void applyRequest(Finding finding, FindingRequest r) {
        try {
            finding.setClassification(FindingClassification.valueOf(r.classification()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Clasificación no válida: " + r.classification());
        }
        finding.setTitle(r.title());
        finding.setDescription(r.description());
        finding.setArea(r.area());
        finding.setDueDate(r.dueDate());
        if (r.status() != null && !r.status().isBlank()) {
            try {
                finding.setStatus(FindingStatus.valueOf(r.status()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Estado de hallazgo no válido: " + r.status());
            }
        }
    }

    private void applyCa(CorrectiveAction ca, CorrectiveActionRequest r) {
        ca.setRootCause(r.rootCause());
        ca.setCorrection(r.correction());
        ca.setPreventiveAction(r.preventiveAction());
        ca.setResponsiblePerson(r.responsiblePerson());
        ca.setTargetDate(r.targetDate());
        ca.setCompletedDate(r.completedDate());
        ca.setVerificationNotes(r.verificationNotes());
        ca.setVerifiedBy(r.verifiedBy());
        if (r.status() != null && !r.status().isBlank()) {
            try {
                ca.setStatus(CorrectiveActionStatus.valueOf(r.status()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Estado de acción correctiva no válido: " + r.status());
            }
        }
    }

    /**
     * Mantiene el estado de la NC coherente con sus acciones correctivas: se cierra cuando todas están verificadas,
     * pasa a «en curso» cuando alguna avanza y se reabre si una acción verificada deja de estarlo o desaparece.
     */
    private void syncFindingStatus(Finding finding) {
        List<CorrectiveAction> actions = correctiveActions.findByFindingIdOrderByIdAsc(finding.getId());
        FindingStatus current = finding.getStatus();
        FindingStatus next = current;
        if (actions.isEmpty()) {
            if (finding.getClassification() != FindingClassification.OBSERVATION && current != FindingStatus.OPEN) {
                next = FindingStatus.OPEN;
            }
        } else {
            boolean allVerified = actions.stream().allMatch(a -> a.getStatus() == CorrectiveActionStatus.VERIFIED);
            boolean anyProgress = actions.stream().anyMatch(a ->
                    a.getStatus() == CorrectiveActionStatus.IN_PROGRESS
                            || a.getStatus() == CorrectiveActionStatus.COMPLETED
                            || a.getStatus() == CorrectiveActionStatus.VERIFIED);
            if (allVerified) {
                next = FindingStatus.CLOSED;
            } else if (current == FindingStatus.CLOSED || current == FindingStatus.VERIFIED) {
                next = anyProgress ? FindingStatus.IN_PROGRESS : FindingStatus.OPEN;
            } else if (current == FindingStatus.OPEN && anyProgress) {
                next = FindingStatus.IN_PROGRESS;
            }
        }
        if (next != current) {
            finding.setStatus(next);
            findings.save(finding);
        }
    }

    private String nextReference(Long auditId) {
        return String.format("NC-%03d", referenceSequences.nextFindingNumber(auditId));
    }

    private FindingDto toDto(Finding f) {
        List<CorrectiveActionDto> actions = correctiveActions.findByFindingIdOrderByIdAsc(f.getId())
                .stream().map(this::toDto).toList();
        ControlPointView cp = f.getControlPointId() == null ? null
                : catalog.findControlPoint(f.getControlPointId()).orElse(null);
        return new FindingDto(
                f.getId(), f.getAuditId(),
                f.getControlPointId(),
                cp == null ? null : cp.ref(),
                cp == null ? null : cp.title(),
                f.getReference(), f.getClassification().name(), f.getTitle(), f.getDescription(),
                f.getArea(), f.getDueDate(), f.getStatus().name(), f.getRaisedAt(), actions,
                attachmentLookup.byEntity(AttachmentEntityType.FINDING, f.getId()));
    }

    private CorrectiveActionDto toDto(CorrectiveAction a) {
        return new CorrectiveActionDto(a.getId(), a.getFindingId(), a.getRootCause(), a.getCorrection(),
                a.getPreventiveAction(), a.getResponsiblePerson(), a.getTargetDate(), a.getCompletedDate(),
                a.getVerificationNotes(), a.getVerifiedBy(), a.getStatus().name(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
