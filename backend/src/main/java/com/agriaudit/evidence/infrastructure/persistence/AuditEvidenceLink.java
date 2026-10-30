package com.agriaudit.evidence.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Vínculo confirmado de un registro con un punto de control de una auditoría. */
@Entity
@Table(schema = "evidence", name = "audit_links")
public class AuditEvidenceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "audit_id", nullable = false)
    private Long auditId;

    @Column(name = "control_point_id", nullable = false)
    private Long controlPointId;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "linked_by_user_id")
    private Long linkedByUserId;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    protected AuditEvidenceLink() {
    }

    public AuditEvidenceLink(Long organizationId, Long auditId, Long controlPointId, Long recordId, Long linkedByUserId) {
        this.organizationId = organizationId;
        this.auditId = auditId;
        this.controlPointId = controlPointId;
        this.recordId = recordId;
        this.linkedByUserId = linkedByUserId;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAuditId() { return auditId; }
    public Long getControlPointId() { return controlPointId; }
    public Long getRecordId() { return recordId; }
    public Long getLinkedByUserId() { return linkedByUserId; }
    public Instant getLinkedAt() { return linkedAt; }
}
