package com.agriaudit.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas del catálogo de estándares que usan los demás módulos (API pública de {@code catalog}).
 *
 * <p>Devuelve vistas inmutables, nunca entidades: los demás módulos guardan solo el identificador del
 * estándar o del punto de control y leen el resto por aquí, sin asociaciones JPA ni {@code JOIN} con
 * las tablas del catálogo (RT-02).
 */
public interface CatalogLookup {

    Optional<StandardView> findStandard(String code);

    Optional<StandardView> findStandard(Long id);

    /** Puntos de control del estándar, ordenados por capítulo y posición. */
    List<ControlPointView> controlPointsOf(String standardCode);

    Optional<ControlPointView> findControlPoint(Long id);

    /** Puntos de control por identificador; los que no existen no aparecen en el mapa. */
    Map<Long, ControlPointView> findControlPoints(Collection<Long> ids);
}
