package com.agriaudit.findings.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.audit.AuditLookup;
import com.agriaudit.audit.AuditRef;
import com.agriaudit.catalog.domain.ControlPoint;
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
        AuditRef audit = audits.requireAudit(auditId);
        Finding finding = new Finding();
        finding.setAuditId(audit.id());
        applyControlPoint(finding, request.controlPointId(), audit);
        applyRequest(finding, request);
        finding.setReference(nextReference(auditId));
        return toDto(findings.save(finding));
    }

    public FindingDto update(Long auditId, Long findingId, FindingRequest request) {
        audits.requireAudit(auditId);
        Finding finding = requireFinding(auditId, findingId);
        AuditRef audit = audits.requireAudit(auditId);
        applyControlPoint(finding, request.controlPointId(), audit);
        applyRequest(finding, request);
        return toDto(findings.save(finding));
    }

    public void delete(Long auditId, Long findingId) {
        audits.requireAudit(auditId);
        findings.delete(requireFinding(auditId, findingId));
    }

    public CorrectiveActionDto addCorrectiveAction(Long auditId, Long findingId, CorrectiveActionRequest request) {
        audits.requireAudit(auditId);
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
        audits.requireAudit(auditId);
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
        Finding finding = requireOwnedFinding(auditId, findingId);
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

    // ---- helpers ----

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
            finding.setControlPoint(null);
            return;
        }
        ControlPoint cp = catalog.findControlPoint(controlPointId)
                .orElseThrow(() -> new NotFoundException("Punto de control no encontrado: " + controlPointId));
        if (!cp.getChapter().getStandard().getCode().equals(audit.standardCode())) {
            throw new BadRequestException("El punto de control no pertenece al estándar de la auditoría");
        }
        finding.setControlPoint(cp);
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

    private void syncFindingStatus(Finding finding) {
        List<CorrectiveAction> actions = correctiveActions.findByFindingIdOrderByIdAsc(finding.getId());
        if (actions.isEmpty()) {
            return;
        }
        boolean allVerified = actions.stream().allMatch(a -> a.getStatus() == CorrectiveActionStatus.VERIFIED);
        boolean anyProgress = actions.stream().anyMatch(a ->
                a.getStatus() == CorrectiveActionStatus.IN_PROGRESS
                        || a.getStatus() == CorrectiveActionStatus.COMPLETED);
        if (allVerified) {
            finding.setStatus(FindingStatus.CLOSED);
        } else if (anyProgress && finding.getStatus() == FindingStatus.OPEN) {
            finding.setStatus(FindingStatus.IN_PROGRESS);
        }
        findings.save(finding);
    }

    private String nextReference(Long auditId) {
        return String.format("NC-%03d", referenceSequences.nextFindingNumber(auditId));
    }

    private FindingDto toDto(Finding f) {
        List<CorrectiveActionDto> actions = correctiveActions.findByFindingIdOrderByIdAsc(f.getId())
                .stream().map(this::toDto).toList();
        return new FindingDto(
                f.getId(), f.getAuditId(),
                f.getControlPoint() == null ? null : f.getControlPoint().getId(),
                f.getControlPoint() == null ? null : f.getControlPoint().getRef(),
                f.getControlPoint() == null ? null : f.getControlPoint().getTitle(),
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
