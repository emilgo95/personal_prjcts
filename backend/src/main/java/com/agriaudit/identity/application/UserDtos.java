package com.agriaudit.identity.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserCreateRequest(
            @NotBlank @Email @Size(max = 200) String email,
            @Size(max = 200) String fullName,
            @NotBlank String role,
            @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password) {
    }

    /** Contraseña temporal que el administrador comunica a la persona; solo se muestra una vez. */
    public record TemporaryPasswordResponse(String temporaryPassword) {
    }

    public record UserUpdateRequest(
            @Size(max = 200) String fullName,
            @NotBlank String role,
            Boolean active,
            @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres") String password) {
    }
}
