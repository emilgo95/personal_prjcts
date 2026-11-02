package com.agriaudit.catalog.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "catalog", name = "audit_standards")
public class AuditStandard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    private String version;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_body")
    private String ownerBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StandardCategory category = StandardCategory.GAP;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "scoring_rule", columnDefinition = "text")
    private String scoringRule;

    @Column(name = "official_url")
    private String officialUrl;

    @Column(nullable = false)
    private boolean active = true;

    /** {@code null} = estándar común (demostración); con valor, privado de esa organización. */
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "imported_at")
    private Instant importedAt;

    /** Plazo de cierre de NC por gravedad, en días desde la auditoría (RF-308); {@code null} = sin plazo. */
    @Column(name = "closure_days_critical")
    private Integer closureDaysCritical = 28;

    @Column(name = "closure_days_major")
    private Integer closureDaysMajor = 28;

    @Column(name = "closure_days_minor")
    private Integer closureDaysMinor = 28;

    @Column(name = "closure_days_observation")
    private Integer closureDaysObservation;

    @Column(name = "imported_by")
    private String importedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "standard", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<StandardChapter> chapters = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerBody() { return ownerBody; }
    public void setOwnerBody(String ownerBody) { this.ownerBody = ownerBody; }
    public StandardCategory getCategory() { return category; }
    public void setCategory(StandardCategory category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getScoringRule() { return scoringRule; }
    public void setScoringRule(String scoringRule) { this.scoringRule = scoringRule; }
    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String officialUrl) { this.officialUrl = officialUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
    public String getImportedBy() { return importedBy; }
    public void setImportedBy(String importedBy) { this.importedBy = importedBy; }
    public boolean isCustom() { return organizationId != null; }
    public Integer getClosureDaysCritical() { return closureDaysCritical; }
    public void setClosureDaysCritical(Integer days) { this.closureDaysCritical = days; }
    public Integer getClosureDaysMajor() { return closureDaysMajor; }
    public void setClosureDaysMajor(Integer days) { this.closureDaysMajor = days; }
    public Integer getClosureDaysMinor() { return closureDaysMinor; }
    public void setClosureDaysMinor(Integer days) { this.closureDaysMinor = days; }
    public Integer getClosureDaysObservation() { return closureDaysObservation; }
    public void setClosureDaysObservation(Integer days) { this.closureDaysObservation = days; }
    public Instant getCreatedAt() { return createdAt; }
    public List<StandardChapter> getChapters() { return chapters; }
}
