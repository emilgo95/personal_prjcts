package com.agriaudit.evidence.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una equivalencia de la matriz: un tipo de evidencia sirve para un punto de control de una norma. */
@Entity
@Table(schema = "evidence", name = "type_control_points")
public class MatrixEntry {

    public static final String DRAFT = "DRAFT";
    public static final String VALIDATED = "VALIDATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "standard_code", nullable = false)
    private String standardCode;

    @Column(name = "control_ref", nullable = false)
    private String controlRef;

    @Column(nullable = false)
    private String status;

    protected MatrixEntry() {
    }

    public MatrixEntry(Long organizationId, String typeCode, String standardCode, String controlRef, String status) {
        this.organizationId = organizationId;
        this.typeCode = typeCode;
        this.standardCode = standardCode;
        this.controlRef = controlRef;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public String getTypeCode() { return typeCode; }
    public String getStandardCode() { return standardCode; }
    public String getControlRef() { return controlRef; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
