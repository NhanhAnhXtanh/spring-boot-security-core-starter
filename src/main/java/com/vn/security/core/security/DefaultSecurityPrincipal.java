package com.vn.security.core.security;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;

/**
 * Default principal used by the starter's built-in user store.
 */
public class DefaultSecurityPrincipal extends AbstractSecurityPrincipal {

    public DefaultSecurityPrincipal(
        String userId,
        String username,
        String password,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities,
        Map<String, Object> claims
    ) {
        super(userId, username, password, enabled, authorities, claims);
    }
}
