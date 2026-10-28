package com.agriaudit.identity.infrastructure.security;

import com.agriaudit.identity.AppUserPrincipal;
import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.trace.ActorProvider;
import com.agriaudit.trace.TraceActor;
import org.springframework.stereotype.Component;

/** Implementa el puerto {@link ActorProvider} de {@code trace} con el usuario autenticado. */
@Component
class SecurityActorProvider implements ActorProvider {

    @Override
    public TraceActor current() {
        AppUserPrincipal p = SecurityUtils.currentPrincipal();
        return new TraceActor(p.getId(), p.getUsername(), p.getFullName(), p.getRole().name(),
                p.getOrganizationId());
    }
}
