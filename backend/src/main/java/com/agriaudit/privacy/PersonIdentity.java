package com.agriaudit.privacy;

/**
 * La persona cuyos datos se exportan. Los módulos guardan su nombre o su correo como texto (quién evaluó, subió,
 * generó…), así que la buscan por esos dos valores y, en la traza, por su código de usuario.
 */
public record PersonIdentity(Long userId, String email, String fullName) {

    /** Valores con los que se busca a la persona en campos de texto: correo y nombre (o el correo dos veces). */
    public Object[] textKeys() {
        return new Object[]{email, fullName == null || fullName.isBlank() ? email : fullName};
    }
}
