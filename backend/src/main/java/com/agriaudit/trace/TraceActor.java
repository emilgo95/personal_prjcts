package com.agriaudit.trace;

/** Quién actúa: usuario autenticado en el momento del cambio. */
public record TraceActor(Long userId, String username, String fullName, String role, Long organizationId) {
}
