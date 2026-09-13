/**
 * Módulo {@code fieldbook}: cuaderno de campo — hoy, las explotaciones; en WS-4, recintos, labores,
 * tratamientos fitosanitarios, insumos y lotes de cosecha (objetivo O1).
 *
 * <p>Hexagonal estricto (RT-01): {@code domain} y {@code port} no dependen de framework ni de
 * persistencia (lo comprueba {@code ArchitectureTests}); el adaptador JPA vive en
 * {@code infrastructure.persistence} y pasa el contrato {@code FarmRepositoryContract}.
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.fieldbook.FarmDirectory} y
 *       {@link com.agriaudit.fieldbook.FarmDtos}.</li>
 *   <li>{@code domain}, {@code port}, {@code application}, {@code infrastructure}: internos.</li>
 * </ul>
 */
@ApplicationModule(displayName = "Cuaderno de campo")
package com.agriaudit.fieldbook;

import org.springframework.modulith.ApplicationModule;
