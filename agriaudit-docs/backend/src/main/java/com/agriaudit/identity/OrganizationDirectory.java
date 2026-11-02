package com.agriaudit.identity;

import java.util.Optional;

/** Consultas sobre organizaciones que usan otros módulos (API pública de {@code identity}). */
public interface OrganizationDirectory {

    Optional<String> organizationName(Long organizationId);

    Optional<Long> findOrganizationIdByName(String name);

    /** Cualquier organización existente; solo para el arranque de datos de demostración. */
    Optional<Long> anyOrganizationId();
}
