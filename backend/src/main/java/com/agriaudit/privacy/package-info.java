/**
 * Módulo {@code privacy}: exportación de todos los datos de una organización (devolución de los datos al terminar el
 * servicio y ayuda para atender derechos, art. 28.3 RGPD).
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.privacy.OrganizationDataContributor} y
 *       {@link com.agriaudit.privacy.ExportSink}. Cada módulo implementa un contribuyente que vuelca SUS datos;
 *       {@code privacy} los reúne en un ZIP. Así {@code privacy} no depende de ningún módulo de negocio y no hay ciclos.</li>
 *   <li>{@code application}, {@code infrastructure}: internos (orquestación del ZIP y endpoint).</li>
 * </ul>
 */
@ApplicationModule(displayName = "Privacidad y exportación")
package com.agriaudit.privacy;

import org.springframework.modulith.ApplicationModule;
