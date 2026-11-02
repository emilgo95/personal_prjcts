package com.agriaudit.trace.infrastructure.persistence;

import com.agriaudit.trace.application.TraceCrypto;

import java.time.Instant;

/** Fila de {@code trace.change_log} tal y como está guardada. */
public record StoredEntry(
        Long id, long organizationId, long seq, Instant occurredAt, Instant createdAt,
        Long userId, String username, String userFullName, String userRole,
        String entityType, Long entityId, String entityRef, String parentType, Long parentId, Long auditId,
        String action, String summary, String changes, String prevHash, String hash, String signature,
        int formatVersion) {

    /** Texto que se resume con SHA-512: todos los campos de la entrada menos la huella y la firma. */
    public String canonical() {
        if (formatVersion == 1) {
            return TraceCrypto.canonical(organizationId, seq, occurredAt, createdAt, userId, username, userFullName,
                    userRole, entityType, entityId, entityRef, parentType, parentId, auditId, action, summary, changes,
                    prevHash);
        }
        // Formato 2: sin nombre ni correo (solo el código de usuario), para poder bloquear la identidad sin tocar la traza.
        return TraceCrypto.canonical(formatVersion, organizationId, seq, occurredAt, createdAt, userId, userRole,
                entityType, entityId, entityRef, parentType, parentId, auditId, action, summary, changes, prevHash);
    }
}
