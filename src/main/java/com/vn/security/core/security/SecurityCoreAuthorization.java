package com.vn.security.core.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Method-security helper that evaluates authorities through the starter's dynamic resolver.
 */
@Component("securityCoreAuthorization")
public class SecurityCoreAuthorization {

    private final CurrentUserAuthorityResolver authorityResolver;

    public SecurityCoreAuthorization(CurrentUserAuthorityResolver authorityResolver) {
        this.authorityResolver = authorityResolver;
    }

    public boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authorityResolver.resolveAuthorities(authentication).contains(authority);
    }
}
