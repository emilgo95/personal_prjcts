/**
 * Módulo {@code storage}: contenido binario y adjuntos de evidencia.
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.storage.BlobStore} (puerto de
 *       almacenamiento, RT-32), {@link com.agriaudit.storage.AttachmentStore},
 *       {@link com.agriaudit.storage.AttachmentDtos} y {@link com.agriaudit.storage.AttachmentEntityType}.</li>
 *   <li>{@code application}, {@code infrastructure}: internos (validación de ficheros, entidad y
 *       repositorio de adjuntos, adaptador de sistema de ficheros, descarga y borrado por id).</li>
 * </ul>
 *
 * <p>No conoce a qué pertenece cada adjunto: la subida con ámbito de auditoría, punto de control o no
 * conformidad la orquesta el módulo dueño de esa entidad, que comprueba la propiedad antes de llamar a
 * {@link com.agriaudit.storage.AttachmentStore}. Así no hay ciclo entre {@code storage} y {@code audit}.
 */
@ApplicationModule(displayName = "Almacenamiento y adjuntos")
package com.agriaudit.storage;

import org.springframework.modulith.ApplicationModule;
