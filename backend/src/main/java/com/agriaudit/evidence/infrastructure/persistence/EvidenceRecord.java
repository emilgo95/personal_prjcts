package com.agriaudit.evidence.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import com.agriaudit.evidence.domain.EvidenceRules;

import java.time.LocalDate;

/** Registro de evidencia de una explotación: se da de alta una vez y se reutiliza en varias auditorías. */
@Entity
@Table(schema = "evidence", name = "evidence_records")
public class EvidenceRecord {

    public static final String FAVORABLE = "FAVORABLE";
    public static final String UNFAVORABLE = "UNFAVORABLE";
    public static final String UNRATED = "UNRATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(nullable = false)
    private String title;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(nullable = false)
    private String outcome = UNRATED;

    private String notes;

    @Column(name = "contains_personal_data", nullable = false)
    private boolean containsPersonalData;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EvidenceRecord() {
    }

    public EvidenceRecord(Long organizationId, Long farmId, String typeCode, Long createdByUserId) {
        this.organizationId = organizationId;
        this.farmId = farmId;
        this.typeCode = typeCode;
        this.createdByUserId = createdByUserId;
    }

    /** Los datos del registro que necesitan las reglas del dominio. */
    public EvidenceRules.Facts facts() {
        return new EvidenceRules.Facts(id, issuedOn, validUntil, outcome);
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getFarmId() { return farmId; }
    public String getTypeCode() { return typeCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public void setIssuedOn(LocalDate issuedOn) { this.issuedOn = issuedOn; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isContainsPersonalData() { return containsPersonalData; }
    public void setContainsPersonalData(boolean containsPersonalData) { this.containsPersonalData = containsPersonalData; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
