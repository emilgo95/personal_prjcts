package com.agriaudit.audit.domain;

import jakarta.persistence.*;

import java.time.Instant;
import com.agriaudit.catalog.domain.ControlPoint;

@Entity
@Table(name = "audit_item_results")
public class AuditItemResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", nullable = false)
    private Long auditId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "control_point_id", nullable = false)
    private ControlPoint controlPoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplianceResult compliance = ComplianceResult.NOT_ASSESSED;

    @Column(name = "evidence_notes", columnDefinition = "text")
    private String evidenceNotes;

    @Column(name = "auditor_comment", columnDefinition = "text")
    private String auditorComment;

    @Column(name = "assessed_at")
    private Instant assessedAt;

    @Column(name = "assessed_by")
    private String assessedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public ControlPoint getControlPoint() { return controlPoint; }
    public void setControlPoint(ControlPoint controlPoint) { this.controlPoint = controlPoint; }
    public ComplianceResult getCompliance() { return compliance; }
    public void setCompliance(ComplianceResult compliance) { this.compliance = compliance; }
    public String getEvidenceNotes() { return evidenceNotes; }
    public void setEvidenceNotes(String evidenceNotes) { this.evidenceNotes = evidenceNotes; }
    public String getAuditorComment() { return auditorComment; }
    public void setAuditorComment(String auditorComment) { this.auditorComment = auditorComment; }
    public Instant getAssessedAt() { return assessedAt; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }
    public String getAssessedBy() { return assessedBy; }
    public void setAssessedBy(String assessedBy) { this.assessedBy = assessedBy; }
}
