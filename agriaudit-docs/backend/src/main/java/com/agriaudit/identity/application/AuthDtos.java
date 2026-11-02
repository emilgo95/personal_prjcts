package com.agriaudit.identity.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record UserDto(
            Long id,
            String email,
            String fullName,
            String role,
            Long organizationId,
            String organizationName,
            boolean active,
            boolean blocked,
            /** Tiene una contraseña temporal: debe cambiarla antes de usar la aplicación. */
            boolean mustChangePassword) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInMinutes,
            UserDto user) {
    }
}
