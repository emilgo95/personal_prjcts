package com.agriaudit.domain;

import com.agriaudit.domain.enums.ControlLevel;
import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "control_points")
public class ControlPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chapter_id", nullable = false)
    private StandardChapter chapter;

    @Column(nullable = false)
    private String ref;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ControlLevel level = ControlLevel.MAJOR_MUST;

    @Column(columnDefinition = "text")
    private String guidance;

    @Column(name = "evidence_examples", columnDefinition = "text")
    private String evidenceExamples;

    @Column(name = "applicable_scopes")
    private String applicableScopes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "control_point_regulations",
            joinColumns = @JoinColumn(name = "control_point_id"),
            inverseJoinColumns = @JoinColumn(name = "regulation_id"))
    private Set<Regulation> regulations = new LinkedHashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public StandardChapter getChapter() { return chapter; }
    public void setChapter(StandardChapter chapter) { this.chapter = chapter; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ControlLevel getLevel() { return level; }
    public void setLevel(ControlLevel level) { this.level = level; }
    public String getGuidance() { return guidance; }
    public void setGuidance(String guidance) { this.guidance = guidance; }
    public String getEvidenceExamples() { return evidenceExamples; }
    public void setEvidenceExamples(String evidenceExamples) { this.evidenceExamples = evidenceExamples; }
    public String getApplicableScopes() { return applicableScopes; }
    public void setApplicableScopes(String applicableScopes) { this.applicableScopes = applicableScopes; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Set<Regulation> getRegulations() { return regulations; }
}
