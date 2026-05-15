package com.vn.security.core.security.bridge;

import java.util.Collection;
import java.util.Optional;

/**
 * Bridge between Spring's {@link org.springframework.security.core.context.SecurityContextHolder}
 * and the merged security engine.
 * <p>
 * The default implementation is {@link DefaultSecurityContextBridge}; the merged engine overrides
 * it by providing a {@code @Primary} bean implementing this interface.
 */
public interface SecurityContextBridge {
    /**
     * Returns the login name of the currently authenticated user, or empty if not authenticated.
     */
    Optional<String> getCurrentUserLogin();

    /**
     * Returns the raw authority names (e.g. "ROLE_ADMIN") for the current user,
     * or an empty collection if not authenticated.
     */
    Collection<String> getCurrentUserAuthorities();

    /**
     * Returns true if the current security context holds an authenticated (non-anonymous) principal.
     */
    boolean isAuthenticated();
}
