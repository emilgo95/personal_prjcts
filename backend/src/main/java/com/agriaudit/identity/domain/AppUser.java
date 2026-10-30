package com.agriaudit.identity.domain;

import com.agriaudit.identity.Role;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(schema = "identity", name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.AUDITOR;

    @Column(nullable = false)
    private boolean active = true;

    /** Datos identificativos bloqueados (art. 32 LOPDGDD): la cuenta no se puede usar ni reactivar. */
    @Column(nullable = false)
    private boolean blocked = false;

    /** Contraseña temporal (puesta por un administrador): hay que cambiarla antes de usar la aplicación. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    /** Último cambio de contraseña: los tokens emitidos antes dejan de valer (se cierran las demás sesiones). */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }

    /** Pone una contraseña nueva (ya cifrada) y deja sin validez las sesiones abiertas con la anterior. */
    public void changePassword(String passwordHash, boolean temporary, Instant now) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = temporary;
        this.passwordChangedAt = now;
    }
}
