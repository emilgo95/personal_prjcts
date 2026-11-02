package com.agriaudit.identity.infrastructure.web;

import com.agriaudit.identity.SecurityUtils;
import com.agriaudit.identity.application.AuthService;
import com.agriaudit.identity.application.AuthDtos.ChangePasswordRequest;
import com.agriaudit.identity.application.AuthDtos.LoginRequest;
import com.agriaudit.identity.application.AuthDtos.RefreshRequest;
import com.agriaudit.identity.application.AuthDtos.TokenResponse;
import com.agriaudit.identity.application.AuthDtos.UserDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public UserDto me() {
        return authService.currentUser(SecurityUtils.currentUsername());
    }

    /** Cambia la contraseña propia; devuelve tokens nuevos (las demás sesiones quedan cerradas). */
    @PostMapping("/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(SecurityUtils.currentUsername(), request.currentPassword(),
                request.newPassword());
    }
}
