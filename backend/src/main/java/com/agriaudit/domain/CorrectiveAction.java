package com.agriaudit.domain;

import com.agriaudit.domain.enums.CorrectiveActionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "corrective_actions")
public class CorrectiveAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "finding_id", nullable = false)
    private Long findingId;

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(columnDefinition = "text")
    private String correction;

    @Column(name = "preventive_action", columnDefinition = "text")
    private String preventiveAction;

    @Column(name = "responsible_person")
    private String responsiblePerson;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "verification_notes", columnDefinition = "text")
    private String verificationNotes;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorrectiveActionStatus status = CorrectiveActionStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFindingId() { return findingId; }
    public void setFindingId(Long findingId) { this.findingId = findingId; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getCorrection() { return correction; }
    public void setCorrection(String correction) { this.correction = correction; }
    public String getPreventiveAction() { return preventiveAction; }
    public void setPreventiveAction(String preventiveAction) { this.preventiveAction = preventiveAction; }
    public String getResponsiblePerson() { return responsiblePerson; }
    public void setResponsiblePerson(String responsiblePerson) { this.responsiblePerson = responsiblePerson; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }
    public String getVerificationNotes() { return verificationNotes; }
    public void setVerificationNotes(String verificationNotes) { this.verificationNotes = verificationNotes; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public CorrectiveActionStatus getStatus() { return status; }
    public void setStatus(CorrectiveActionStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
