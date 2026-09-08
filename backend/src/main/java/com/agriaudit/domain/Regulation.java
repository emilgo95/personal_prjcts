package com.agriaudit.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "regulations")
public class Regulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    private String jurisdiction;

    @Column(name = "scope_area")
    private String scopeArea;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "key_obligations", columnDefinition = "text")
    private String keyObligations;

    @Column(name = "source_url")
    private String sourceUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }
    public String getScopeArea() { return scopeArea; }
    public void setScopeArea(String scopeArea) { this.scopeArea = scopeArea; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getKeyObligations() { return keyObligations; }
    public void setKeyObligations(String keyObligations) { this.keyObligations = keyObligations; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
