package com.agriaudit.fieldbook.port;

import com.agriaudit.fieldbook.domain.Farm;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de explotaciones. Toda lectura va acotada por organización (aislamiento
 * multitenant, RS-AUTHZ-04). El contrato lo fija {@code FarmRepositoryContract} en los tests.
 */
public interface FarmRepository {

    Farm save(Farm farm);

    Optional<Farm> findById(long id, long organizationId);

    /** Explotaciones de la organización, ordenadas por razón social. */
    List<Farm> findByOrganization(long organizationId);

    List<Farm> findByIds(long organizationId, Collection<Long> ids);

    long countByOrganization(long organizationId);

    boolean existsAny();

    void delete(Farm farm);
}
