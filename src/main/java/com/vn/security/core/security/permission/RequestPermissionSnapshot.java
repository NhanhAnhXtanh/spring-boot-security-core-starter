package com.vn.security.core.security.permission;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.vn.security.core.security.CurrentUserAuthorityResolver;
import com.vn.security.core.security.domain.SecPermission;
import com.vn.security.core.security.store.SecPermissionStore;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Request-scoped permission snapshot that caches authority resolution and permission
 * matrix construction once per HTTP request, and shares a cross-request PermissionMatrix
 * cache in Hazelcast keyed by the current user's resolved authority-name set.
 *
 * <p>This bean is active only within an HTTP request context. Callers outside a request
 * (batch jobs, tests, non-web contexts) must check {@link #isRequestScopeActive()} and
 * fall back to direct store queries.
 */
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestPermissionSnapshot {

    /** Name of the Hazelcast map used as the cross-request PermissionMatrix cache. */
    public static final String PERMISSION_MATRIX_CACHE = "sec-permission-matrix";

    private final SecPermissionStore secPermissionStore;
    private final HazelcastInstance hazelcastInstance;
    private final CurrentUserAuthorityResolver authorityResolver;

    /** Cached authority names for the current request; null if not yet loaded. */
    private Collection<String> cachedAuthorities;

    /** Cached permission matrix for the current request; null if not yet loaded. */
    private PermissionMatrix cachedMatrix;

    public RequestPermissionSnapshot(
        SecPermissionStore secPermissionStore,
        HazelcastInstance hazelcastInstance,
        CurrentUserAuthorityResolver authorityResolver
    ) {
        this.secPermissionStore = secPermissionStore;
        this.hazelcastInstance = hazelcastInstance;
        this.authorityResolver = authorityResolver;
    }

    /**
     * Returns true when a Spring Web request context is active (i.e., inside an HTTP request).
     * Use this guard before calling snapshot methods from non-web callers.
     */
    public static boolean isRequestScopeActive() {
        return RequestContextHolder.getRequestAttributes() != null;
    }

    /**
     * Returns the current user's resolved authority names for this request.
     * Returns the cached result on subsequent calls within the same request.
     */
    public Collection<String> getAuthorities() {
        if (cachedAuthorities == null) {
            cachedAuthorities = loadAuthorities();
        }
        return cachedAuthorities;
    }

    /**
     * Returns the permission matrix for the current request.
     *
     * <p>On first call, derives a deterministic cache key from the sorted authority names,
     * checks the shared Hazelcast cache, and only queries the DB if no cached entry exists.
     * Returns the same instance on subsequent calls within the same request.
     */
    public PermissionMatrix getMatrix() {
        if (cachedMatrix == null) {
            Collection<String> authorities = getAuthorities();
            if (authorities.isEmpty()) {
                cachedMatrix = PermissionMatrix.EMPTY;
            } else {
                String cacheKey = toCacheKey(authorities);
                IMap<String, PermissionMatrix> cache = hazelcastInstance.getMap(PERMISSION_MATRIX_CACHE);
                cachedMatrix = cache.computeIfAbsent(cacheKey, k -> buildMatrix(authorities));
            }
        }
        return cachedMatrix;
    }

    /**
     * Resolves the current user's authority names from the security context.
     */
    private Collection<String> loadAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return List.of();
        }
        return List.copyOf(authorityResolver.resolveAuthorities(auth));
    }

    /**
     * Builds a {@link PermissionMatrix} from a bulk DB query for the given authority names.
     * This is only called on Hazelcast cache miss.
     */
    private PermissionMatrix buildMatrix(Collection<String> authorities) {
        List<SecPermission> allPerms = secPermissionStore.findAllByAuthorityNameIn(authorities);
        return new PermissionMatrix(allPerms);
    }

    /**
     * Derives a deterministic, order-independent cache key from the given authority names.
     * Uses an explicit {@code |}-join over a sorted set so the key format does not depend on
     * {@link java.util.AbstractCollection#toString()} (which is not part of the JDK contract)
     * and so that {@code {ROLE_A, ROLE_B}} and {@code {ROLE_B, ROLE_A}} map to the same entry.
     */
    static String toCacheKey(Collection<String> authorities) {
        return String.join("|", new TreeSet<>(authorities));
    }
}
