/**
 * Núcleo compartido: excepciones de aplicación que usan todos los módulos. Módulo abierto (sin
 * internals); no debe depender de ningún otro módulo.
 */
@ApplicationModule(displayName = "Común", type = ApplicationModule.Type.OPEN)
package com.agriaudit.common;

import org.springframework.modulith.ApplicationModule;
