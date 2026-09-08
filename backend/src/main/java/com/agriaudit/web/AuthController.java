package com.agriaudit.web;

import com.agriaudit.security.SecurityUtils;
import com.agriaudit.service.AuthService;
import com.agriaudit.web.dto.AuthDtos.LoginRequest;
import com.agriaudit.web.dto.AuthDtos.RefreshRequest;
import com.agriaudit.web.dto.AuthDtos.TokenResponse;
import com.agriaudit.web.dto.AuthDtos.UserDto;
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
}
