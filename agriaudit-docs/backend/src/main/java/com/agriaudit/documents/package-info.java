/**
 * Módulo {@code documents}: generación de los documentos oficiales de una auditoría (plan, checklist,
 * informe de NC tipo FA05, CAPR, informe de auditoría, GRASP, SMETA, resumen de cumplimiento) en PDF,
 * DOCX y XLSX, su registro con {@code sha256} y su descarga.
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.documents.DocumentLookup} y
 *       {@link com.agriaudit.documents.DocumentDtos}.</li>
 *   <li>{@code application}: generación, ensamblado del {@code DocumentModel} a partir de las API de
 *       {@code audit}, {@code findings}, {@code catalog}, {@code identity} y {@code storage}, y el puerto
 *       {@code DocumentRenderer}.</li>
 *   <li>{@code infrastructure.render}: motores PDF (Thymeleaf + openhtmltopdf), DOCX y XLSX (Apache POI).
 *       Las plantillas viven en {@code resources/templates/docs}.</li>
 *   <li>{@code domain}, {@code infrastructure.persistence}, {@code web}, {@code demo}: internos.</li>
 * </ul>
 *
 * <p>El paquete de cierre de NC en un clic (RF-305, WS-2) se construirá aquí, sobre la misma generación.
 */
@ApplicationModule(displayName = "Documentos")
package com.agriaudit.documents;

import org.springframework.modulith.ApplicationModule;
