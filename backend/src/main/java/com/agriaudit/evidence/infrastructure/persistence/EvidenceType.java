package com.agriaudit.evidence.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "evidence", name = "evidence_types")
public class EvidenceType {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "personal_data_notice")
    private String personalDataNotice;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected EvidenceType() {
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getPersonalDataNotice() { return personalDataNotice; }
    public int getSortOrder() { return sortOrder; }
}
