package com.agriaudit.identity.application;

import com.agriaudit.common.BadRequestException;
import com.agriaudit.common.NotFoundException;
import com.agriaudit.identity.domain.AppUser;
import com.agriaudit.identity.infrastructure.persistence.AppUserRepository;
import com.agriaudit.identity.AppUserPrincipal;
import com.agriaudit.identity.infrastructure.security.JwtService;
import com.agriaudit.identity.application.AuthDtos.TokenResponse;
import com.agriaudit.identity.application.AuthDtos.UserDto;
import com.agriaudit.trace.ChangeEntry;
import com.agriaudit.trace.ChangeRecorder;
import com.agriaudit.trace.Changes;
import com.agriaudit.trace.TraceAction;
import com.agriaudit.trace.TraceEntity;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ChangeRecorder trace;

    public AuthService(AuthenticationManager authenticationManager,
                       AppUserRepository users,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       ChangeRecorder trace) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.trace = trace;
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
        AppUserPrincipal principal = new AppUserPrincipal(user);
        // Tras un cambio de contraseña, los refrescos de las sesiones anteriores ya no valen.
        if (!principal.acceptsTokenIssuedAt(claims.getIssuedAt())) {
            throw new BadCredentialsException("Sesión cerrada por un cambio de contraseña");
        }
        return buildResponse(principal);
    }

    /**
     * Cambia la contraseña del usuario que ha iniciado sesión. Pide la actual, deja sin validez las demás sesiones
     * abiertas y devuelve tokens nuevos para seguir en esta. Quita la marca de contraseña temporal.
     */
    @Transactional
    public TokenResponse changePassword(String email, String currentPassword, String newPassword) {
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        // 400 y no 401: un 401 haría que la aplicación cerrase la sesión por una contraseña mal escrita.
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }
        PasswordPolicy.requireValid(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("La contraseña nueva debe ser distinta de la actual");
        }
        boolean wasTemporary = user.isMustChangePassword();
        user.changePassword(passwordEncoder.encode(newPassword), false, java.time.Instant.now());
        AppUser saved = users.save(user);
        trace.record(ChangeEntry.of(TraceEntity.USER, saved.getId(), "usuario " + saved.getId(), null,
                TraceAction.UPDATE, wasTemporary ? "Cambió su contraseña temporal" : "Cambió su contraseña",
                Changes.create().track("password", "Contraseña", "(anterior)", "(cambiada por el usuario)").list()));
        return buildResponse(new AppUserPrincipal(saved));
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
                user.getOrganization().getName(),
                user.isActive(),
                user.isBlocked(),
                user.isMustChangePassword());
    }
}
