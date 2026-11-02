package com.agriaudit.identity.infrastructure.security;

import com.agriaudit.identity.infrastructure.persistence.AppUserRepository;
import com.agriaudit.trace.ActorDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Implementa el puerto {@link ActorDirectory} de {@code trace}: el nombre actual de cada usuario (los bloqueados salen ocultos). */
@Component
class UserActorDirectory implements ActorDirectory {

    private final AppUserRepository users;

    UserActorDirectory(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ActorName> names(Collection<Long> userIds) {
        Map<Long, ActorName> out = new HashMap<>();
        // De un usuario bloqueado solo se muestra su código, nunca el marcador interno que sustituye a su correo.
        users.findAllById(userIds).forEach(u -> out.put(u.getId(),
                new ActorName(u.isBlocked() ? "usuario-" + u.getId() : u.getEmail(), u.getFullName())));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<Long> userIdsMatching(Long organizationId, String text) {
        return new java.util.HashSet<>(users.findIdsMatching(organizationId, text));
    }
}
