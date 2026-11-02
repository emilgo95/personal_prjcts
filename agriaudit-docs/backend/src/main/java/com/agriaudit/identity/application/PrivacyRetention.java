package com.agriaudit.identity.application;

import com.agriaudit.identity.infrastructure.persistence.OrganizationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Plazo de conservación de los datos bloqueados. No hay un plazo oficial único: lo fija quien es responsable del
 * tratamiento (la cooperativa) con asesoramiento legal. Cada organización puede tener el suyo; si no, vale el general
 * ({@code AGRIAUDIT_BLOCKED_RETENTION_MONTHS}). Con el perfil {@code prod} el general es obligatorio.
 */
@Component
public class PrivacyRetention {

    /** Solo para desarrollo y demostración: NO es un plazo legal. */
    public static final int DEVELOPMENT_EXAMPLE_MONTHS = 60;
    public static final int MIN_MONTHS = 1;
    public static final int MAX_MONTHS = 240;

    private final OrganizationRepository organizations;
    private final Integer configured;

    public PrivacyRetention(OrganizationRepository organizations,
                            @Value("${agriaudit.privacy.blocked-retention-months:}") String configured) {
        this.organizations = organizations;
        this.configured = configured == null || configured.isBlank() ? null : Integer.valueOf(configured.trim());
    }

    /** Valor general (configurado o, si no hay, el de ejemplo de desarrollo). */
    public int defaultMonths() {
        return configured != null ? configured : DEVELOPMENT_EXAMPLE_MONTHS;
    }

    public int monthsFor(Long organizationId) {
        Integer own = organizations.findById(organizationId).map(o -> o.getBlockedRetentionMonths()).orElse(null);
        return own != null ? own : defaultMonths();
    }
}
