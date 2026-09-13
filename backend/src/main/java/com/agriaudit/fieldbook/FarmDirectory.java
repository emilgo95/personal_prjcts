package com.agriaudit.fieldbook;

import com.agriaudit.fieldbook.FarmDtos.FarmDto;
import com.agriaudit.fieldbook.FarmDtos.FarmRequest;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Explotaciones vistas desde otros módulos (API pública de {@code fieldbook}). Todas las consultas
 * reciben la organización de forma explícita: el llamante es responsable de pasar la del usuario.
 */
public interface FarmDirectory {

    Optional<FarmDto> findFarm(Long organizationId, Long farmId);

    /** Explotaciones de la organización por id; los ids ajenos o inexistentes no aparecen. */
    Map<Long, FarmDto> findFarms(Long organizationId, Collection<Long> farmIds);

    long countFarms(Long organizationId);

    boolean anyFarmExists();

    /** Alta sin usuario en contexto; solo para el arranque de datos de demostración. */
    FarmDto registerFarm(Long organizationId, FarmRequest request);
}
