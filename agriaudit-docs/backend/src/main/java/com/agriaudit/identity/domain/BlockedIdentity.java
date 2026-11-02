package com.agriaudit.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Datos identificativos reservados de un usuario bloqueado: no se muestran ni se tratan y se destruyen al vencer el plazo. */
@Entity
@Table(schema = "identity", name = "blocked_identity")
public class BlockedIdentity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;

    @Column(name = "blocked_by")
    private Long blockedBy;

    @Column(name = "destroy_after", nullable = false)
    private Instant destroyAfter;

    protected BlockedIdentity() {
    }

    public BlockedIdentity(Long userId, Long organizationId, String email, String fullName, Instant blockedAt,
                           Long blockedBy, Instant destroyAfter) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.email = email;
        this.fullName = fullName;
        this.blockedAt = blockedAt;
        this.blockedBy = blockedBy;
        this.destroyAfter = destroyAfter;
    }

    public Long getUserId() { return userId; }
    public Long getOrganizationId() { return organizationId; }
    public Instant getDestroyAfter() { return destroyAfter; }
    public Instant getBlockedAt() { return blockedAt; }
    public Long getBlockedBy() { return blockedBy; }
}
