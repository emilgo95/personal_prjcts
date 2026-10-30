package com.agriaudit.identity.application;

import com.agriaudit.common.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Reglas de las contraseñas y generación de las temporales que da un administrador. */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    /** BCrypt solo tiene en cuenta los primeros 72 bytes: una contraseña más larga se rechaza en lugar de recortarse. */
    static final int MAX_BYTES = 72;

    /** Sin caracteres que se confunden al dictarlos o leerlos (0/O, 1/l/I). */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
    }

    /** Comprueba una contraseña nueva; lanza {@link BadRequestException} con el motivo si no vale. */
    public static void requireValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BadRequestException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BadRequestException("La contraseña es demasiado larga (máximo " + MAX_BYTES + " caracteres)");
        }
        if (password.isBlank()) {
            throw new BadRequestException("La contraseña no puede ser solo espacios");
        }
    }

    /** Contraseña temporal fácil de dictar: tres grupos de cuatro caracteres (unos 70 bits de azar). */
    public static String temporary() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
