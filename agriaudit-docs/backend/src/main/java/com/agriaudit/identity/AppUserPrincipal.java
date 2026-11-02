package com.agriaudit.identity;

import com.agriaudit.identity.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final Long organizationId;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final Role role;
    private final boolean active;
    private final boolean mustChangePassword;
    private final Instant passwordChangedAt;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.organizationId = user.getOrganization().getId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.isActive();
        this.mustChangePassword = user.isMustChangePassword();
        this.passwordChangedAt = user.getPasswordChangedAt();
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Role getRole() { return role; }
    public String getFullName() { return fullName; }
    public boolean isMustChangePassword() { return mustChangePassword; }

    /**
     * Si un token emitido en {@code issuedAt} sigue valiendo: deja de valer al cambiar la contraseña. Los tokens
     * llevan la hora en segundos, así que se compara con el cambio redondeado al segundo (los emitidos en el mismo
     * segundo del cambio, como el que devuelve el propio cambio, siguen valiendo).
     */
    public boolean acceptsTokenIssuedAt(java.util.Date issuedAt) {
        if (passwordChangedAt == null) {
            return true;
        }
        return issuedAt != null
                && !issuedAt.toInstant().isBefore(passwordChangedAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}
