package com.agriaudit.fieldbook.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Explotación (socio, en una cooperativa): la raíz de la jerarquía {@code organización → explotación
 * → recinto} del cuaderno de campo (RF-T08). Modelo de dominio sin dependencias de framework ni de
 * persistencia (RT-01).
 */
public final class Farm {

    private final Long id;
    private final long organizationId;
    private FarmProfile profile;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Farm(Long id, long organizationId, FarmProfile profile, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.profile = Objects.requireNonNull(profile, "profile");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Alta de una explotación nueva (aún sin identificador). */
    public static Farm register(long organizationId, FarmProfile profile) {
        return new Farm(null, organizationId, profile, null, null);
    }

    /** Reconstrucción desde el almacenamiento. */
    public static Farm restore(Long id, long organizationId, FarmProfile profile, Instant createdAt, Instant updatedAt) {
        return new Farm(Objects.requireNonNull(id, "id"), organizationId, profile, createdAt, updatedAt);
    }

    public void updateProfile(FarmProfile newProfile) {
        this.profile = Objects.requireNonNull(newProfile, "profile");
    }

    public boolean belongsTo(long organizationId) {
        return this.organizationId == organizationId;
    }

    public Long id() { return id; }
    public long organizationId() { return organizationId; }
    public FarmProfile profile() { return profile; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
