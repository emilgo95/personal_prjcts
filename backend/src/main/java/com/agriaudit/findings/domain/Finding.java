package com.agriaudit.findings.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(schema = "findings", name = "findings")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", nullable = false)
    private Long auditId;

    /** Punto de control (módulo catalog), opcional; referencia por id, sin asociación JPA entre módulos. */
    @Column(name = "control_point_id")
    private Long controlPointId;

    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingClassification classification = FindingClassification.MINOR;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String area;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingStatus status = FindingStatus.OPEN;

    @CreationTimestamp
    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt;

    /** Momento en que quedó cerrada o verificada; {@code null} mientras está abierta (RF-301). */
    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public Long getControlPointId() { return controlPointId; }
    public void setControlPointId(Long controlPointId) { this.controlPointId = controlPointId; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public FindingClassification getClassification() { return classification; }
    public void setClassification(FindingClassification classification) { this.classification = classification; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public FindingStatus getStatus() { return status; }
    /**
     * Cambia el estado y mantiene la fecha de cierre: se fija al pasar a cerrada o verificada y se borra si la NC
     * se reabre.
     */
    public void setStatus(FindingStatus status) {
        boolean wasClosed = isClosed(this.status);
        boolean closes = isClosed(status);
        if (closes && !wasClosed) {
            this.closedAt = Instant.now();
        } else if (!closes) {
            this.closedAt = null;
        }
        this.status = status;
    }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    private static boolean isClosed(FindingStatus s) {
        return s == FindingStatus.CLOSED || s == FindingStatus.VERIFIED;
    }
    public Instant getRaisedAt() { return raisedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
