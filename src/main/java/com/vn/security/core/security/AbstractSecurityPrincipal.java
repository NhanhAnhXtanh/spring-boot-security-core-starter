package com.vn.security.core.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;

/**
 * Base implementation for consumer-specific security principals.
 *
 * <p>It implements Spring Security's required account-state methods with the
 * common defaults used by this starter. Applications that need lock/expiry
 * semantics can override the relevant methods in their concrete principal.
 */
public abstract class AbstractSecurityPrincipal implements SecurityPrincipal, AcceptsGrantedAuthorities {

    private final String userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Map<String, Object> claims;
    private Collection<GrantedAuthority> grantedAuthorities;

    protected AbstractSecurityPrincipal(
        Object userId,
        String username,
        String password,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities,
        Map<String, Object> claims
    ) {
        this.userId = userId == null ? null : Objects.toString(userId);
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.grantedAuthorities = List.copyOf(authorities);
        this.claims = Map.copyOf(claims);
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getLogin() {
        return username;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return grantedAuthorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getGrantedAuthorities() {
        return grantedAuthorities;
    }

    @Override
    public void setGrantedAuthorities(Collection<? extends GrantedAuthority> grantedAuthorities) {
        this.grantedAuthorities = List.copyOf(grantedAuthorities);
    }

    @Override
    public Map<String, Object> getClaims() {
        return claims;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
