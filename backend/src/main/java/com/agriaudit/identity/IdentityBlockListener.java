package com.agriaudit.identity;

/**
 * Puerto: cuando se bloquea a un usuario, cada módulo que guardó su nombre o correo como texto (quién evaluó, subió,
 * generó, verificó…) lo sustituye por el marcador. Lo declara {@code identity} y lo implementan los demás módulos.
 */
public interface IdentityBlockListener {

    /**
     * @param email       correo que tenía el usuario (también valor de «usuario» en muchos campos)
     * @param fullName    nombre que tenía (puede ser {@code null})
     * @param placeholder texto que ocupa su lugar, p. ej. «Usuario bloqueado»
     */
    void identityBlocked(Long organizationId, String email, String fullName, String placeholder);
}
