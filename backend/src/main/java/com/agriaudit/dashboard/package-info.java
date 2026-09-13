/**
 * Módulo {@code dashboard}: panel con los indicadores de la organización. No tiene datos propios ni API:
 * compone las API públicas de {@code audit}, {@code findings}, {@code fieldbook} y {@code documents}.
 * Aquí llegarán las métricas de dirección de NC (RF-307) y el panel de salud del SGC (RF-406).
 */
@ApplicationModule(displayName = "Panel")
package com.agriaudit.dashboard;

import org.springframework.modulith.ApplicationModule;
