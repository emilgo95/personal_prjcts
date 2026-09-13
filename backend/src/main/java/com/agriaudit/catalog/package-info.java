/**
 * Módulo {@code catalog}: estándares de auditoría, capítulos, puntos de control y normativa.
 *
 * <p>Catálogo compartido entre organizaciones (no multitenant) y CRUD plano, así que no aplica
 * hexagonal estricto: Spring Data directo detrás de la API del módulo.
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.catalog.CatalogLookup} para los demás
 *       módulos, {@link com.agriaudit.catalog.CatalogService} y {@link com.agriaudit.catalog.CatalogDtos}.</li>
 *   <li>{@code domain}: entidades, expuestas como interfaz con nombre mientras otros módulos las
 *       referencien con asociaciones JPA.</li>
 *   <li>{@code infrastructure}: repositorios y controlador REST, internos del módulo.</li>
 * </ul>
 */
@ApplicationModule(displayName = "Catálogo de estándares")
package com.agriaudit.catalog;

import org.springframework.modulith.ApplicationModule;
