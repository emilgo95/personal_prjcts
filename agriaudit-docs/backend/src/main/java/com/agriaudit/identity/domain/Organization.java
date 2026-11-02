package com.agriaudit.identity.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(schema = "identity", name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String country;

    @Column(nullable = false)
    private String plan = "STANDARD";

    /** Meses que se conservan, bloqueados, los datos de un usuario bloqueado; {@code null} = valor general. */
    @Column(name = "blocked_retention_months")
    private Integer blockedRetentionMonths;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Integer getBlockedRetentionMonths() { return blockedRetentionMonths; }
    public void setBlockedRetentionMonths(Integer months) { this.blockedRetentionMonths = months; }
}
