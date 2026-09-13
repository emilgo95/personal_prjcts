package com.agriaudit.catalog;

import com.agriaudit.catalog.domain.AuditStandard;
import com.agriaudit.catalog.domain.ControlPoint;

import java.util.List;
import java.util.Optional;

/**
 * Consultas del catálogo de estándares que usan los demás módulos (API pública de {@code catalog}).
 *
 * <p>Nadie fuera del módulo accede a sus repositorios. De momento devuelve las entidades del
 * catálogo ({@code catalog.domain}), porque {@code Audit}, {@code AuditItemResult} y {@code Finding}
 * las referencian con asociaciones JPA; sustituirlas por identificadores es el siguiente paso (RT-02).
 */
public interface CatalogLookup {

    Optional<AuditStandard> findStandard(String code);

    /** Puntos de control del estándar, ordenados por capítulo y posición. */
    List<ControlPoint> controlPointsOf(String standardCode);

    Optional<ControlPoint> findControlPoint(Long id);
}
