package com.vn.security.core.security.bridge;

import com.vn.security.core.security.SecurityUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Default {@link SecurityContextBridge} implementation backed directly by Spring Security's
 * {@link SecurityContextHolder}.
 * <p>
 * Registered as a plain {@code @Component} (no {@code @Primary}) so any {@code @Primary} bean
 * implementing {@link SecurityContextBridge} — e.g. {@link MergedSecurityContextBridge} — wins.
 */
@Component
public class DefaultSecurityContextBridge implements SecurityContextBridge {

    @Override
    public Optional<String> getCurrentUserLogin() {
        return SecurityUtils.getCurrentUserLogin();
    }

    @Override
    public Collection<String> getCurrentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Override
    public boolean isAuthenticated() {
        return SecurityUtils.isAuthenticated();
    }
}
