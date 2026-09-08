package com.agriaudit.security;

import com.agriaudit.common.BadRequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AppUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new BadRequestException("No hay un usuario autenticado en el contexto");
        }
        return principal;
    }

    public static Long currentOrganizationId() {
        return currentPrincipal().getOrganizationId();
    }

    public static String currentUsername() {
        return currentPrincipal().getUsername();
    }

    public static Long currentUserId() {
        return currentPrincipal().getId();
    }
}
