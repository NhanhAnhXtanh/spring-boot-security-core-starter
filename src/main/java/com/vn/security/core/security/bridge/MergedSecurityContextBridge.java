package com.vn.security.core.security.bridge;

import com.vn.security.core.security.CurrentUserAuthorityResolver;
import com.vn.security.core.security.permission.RequestPermissionSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@code @Primary} {@link SecurityContextBridge} implementation that filters out phantom authority
 * names — JWT claims that are not backed by a row in {@code sec_authority} — before returning
 * authority names to callers.
 * <p>
 * This bean supersedes {@link DefaultSecurityContextBridge} (which remains as a non-primary
 * fallback). Enforcement code should depend on
 * {@link com.vn.security.core.security.MergedSecurityService} rather than this class directly.
 */
@Primary
@Component
public class MergedSecurityContextBridge implements SecurityContextBridge {

    private final RequestPermissionSnapshot requestPermissionSnapshot;
    private final CurrentUserAuthorityResolver authorityResolver;

    public MergedSecurityContextBridge(
        RequestPermissionSnapshot requestPermissionSnapshot,
        CurrentUserAuthorityResolver authorityResolver
    ) {
        this.requestPermissionSnapshot = requestPermissionSnapshot;
        this.authorityResolver = authorityResolver;
    }

    @Override
    public Optional<String> getCurrentUserLogin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    @Override
    public Collection<String> getCurrentUserAuthorities() {
        // Use request-scoped snapshot when available to avoid repeated DB queries per request.
        if (RequestPermissionSnapshot.isRequestScopeActive()) {
            return requestPermissionSnapshot.getAuthorities();
        }
        // Fallback for non-web contexts (tests, batch, scheduled tasks).
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return List.of();
        }
        return authorityResolver.resolveAuthorities(auth);
    }

    @Override
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (
            auth != null &&
            auth.isAuthenticated() &&
            authorityResolver.resolveAuthorities(auth)
                .stream()
                .noneMatch("ROLE_ANONYMOUS"::equals)
        );
    }
}
