/**
 * Módulo {@code identity}: organizaciones (tenants), usuarios, roles, autenticación JWT y la
 * configuración de seguridad HTTP.
 *
 * <ul>
 *   <li><b>API pública</b> (este paquete): {@link com.agriaudit.identity.SecurityUtils} (usuario y
 *       organización de la petición en curso), {@link com.agriaudit.identity.AppUserPrincipal},
 *       {@link com.agriaudit.identity.Role}, {@link com.agriaudit.identity.OrganizationDirectory} y
 *       {@link com.agriaudit.identity.UserProvisioning}.</li>
 *   <li>{@code domain}, {@code application}, {@code infrastructure}: internos. Cambiar el proveedor de
 *       identidad (OIDC, RS-IAM-01) debe afectar solo a este módulo.</li>
 * </ul>
 */
@ApplicationModule(displayName = "Identidad y acceso")
package com.agriaudit.identity;

import org.springframework.modulith.ApplicationModule;
