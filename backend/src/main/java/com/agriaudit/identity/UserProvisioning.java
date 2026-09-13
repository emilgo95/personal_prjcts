package com.agriaudit.identity;

/** Alta de usuarios desde otros módulos (API pública de {@code identity}); hoy, los datos de demostración. */
public interface UserProvisioning {

    /** Crea el usuario activo si no existe un usuario con ese correo; si existe, no hace nada. */
    void ensureUser(Long organizationId, String email, String fullName, Role role, String rawPassword);
}
