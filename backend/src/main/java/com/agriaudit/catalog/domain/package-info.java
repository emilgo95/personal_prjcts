/**
 * Entidades del catálogo. Interfaz con nombre {@code catalog::domain}: accesible desde otros módulos
 * de forma transitoria, mientras {@code Audit}, {@code AuditItemResult} y {@code Finding} las
 * referencien con asociaciones JPA.
 */
@NamedInterface("domain")
package com.agriaudit.catalog.domain;

import org.springframework.modulith.NamedInterface;
