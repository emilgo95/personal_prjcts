package com.agriaudit.service;

import com.agriaudit.common.NotFoundException;
import com.agriaudit.domain.AppUser;
import com.agriaudit.repo.AppUserRepository;
import com.agriaudit.security.AppUserPrincipal;
import com.agriaudit.security.JwtService;
import com.agriaudit.web.dto.AuthDtos.TokenResponse;
import com.agriaudit.web.dto.AuthDtos.UserDto;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
                       AppUserRepository users,
                       JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        return buildResponse(principal);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (Exception ex) {
            throw new BadCredentialsException("Token de refresco no válido");
        }
        if (!"refresh".equals(claims.get("type"))) {
            throw new BadCredentialsException("Token de refresco no válido");
        }
        AppUser user = users.findByEmailIgnoreCase(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        if (!user.isActive()) {
            throw new BadCredentialsException("Usuario inactivo");
        }
        return buildResponse(new AppUserPrincipal(user));
    }

    @Transactional(readOnly = true)
    public UserDto currentUser(String email) {
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return toDto(user);
    }

    private TokenResponse buildResponse(AppUserPrincipal principal) {
        AppUser user = users.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        return new TokenResponse(
                jwtService.generateAccessToken(principal),
                jwtService.generateRefreshToken(principal),
                "Bearer",
                jwtService.getExpirationMinutes(),
                toDto(user));
    }

    private UserDto toDto(AppUser user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getOrganization().getId(),
                user.getOrganization().getName());
    }
}
