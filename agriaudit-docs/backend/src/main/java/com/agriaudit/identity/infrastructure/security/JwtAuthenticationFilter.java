package com.agriaudit.identity.infrastructure.security;

import com.agriaudit.identity.AppUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Código del error que pide cambiar la contraseña temporal antes de seguir. */
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if (!"access".equals(claims.get("type"))) {
                    filterChain.doFilter(request, response);
                    return;
                }
                UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
                AppUserPrincipal principal = (AppUserPrincipal) userDetails;
                // Un token anterior al último cambio de contraseña ya no vale (sesión cerrada).
                if (userDetails.isEnabled() && principal.acceptsTokenIssuedAt(claims.getIssuedAt())) {
                    if (principal.isMustChangePassword() && !allowedWithTemporaryPassword(request)) {
                        writePasswordChangeRequired(response);
                        return;
                    }
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Con una contraseña temporal solo se puede consultar quién eres y cambiarla. */
    private static boolean allowedWithTemporaryPassword(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) && "/api/auth/me".equals(path))
                || ("POST".equals(request.getMethod()) && "/api/auth/password".equals(path));
    }

    private static void writePasswordChangeRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Texto fijo, sin datos de la petición: no hace falta escapar nada.
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"code\":\"" + PASSWORD_CHANGE_REQUIRED
                + "\",\"message\":\"Tienes que cambiar la contraseña temporal antes de seguir.\"}");
    }
}
