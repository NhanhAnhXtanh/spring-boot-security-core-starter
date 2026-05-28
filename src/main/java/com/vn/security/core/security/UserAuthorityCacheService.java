package com.vn.security.core.security;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.vn.security.core.security.permission.RequestPermissionSnapshot;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Consumer-facing helper for managing the authority caches:
 * <ul>
 *   <li>{@link AuthorityCacheNames#USER_AUTHORITIES_BY_USERNAME} — username → resolved authorities.</li>
 *   <li>{@link RequestPermissionSnapshot#PERMISSION_MATRIX_CACHE} — sorted-role-set → permission matrix.</li>
 * </ul>
 *
 * <p>Neither cache has a TTL; freshness depends entirely on explicit eviction. Required call sites:
 *
 * <ul>
 *   <li>Logout — {@link #evict(String)} for the logged-out username.</li>
 *   <li>Disable / soft-delete user — {@link #evict(String)}.</li>
 *   <li>Change a single user's role membership — {@link #evict(String)}.</li>
 *   <li>Update / delete a role definition (consumer-owned admin endpoints) —
 *       {@link #evictByAuthority(String)} for the affected authority name.</li>
 *   <li>Bulk role / group remap affecting many users — {@link #evictAll()}.</li>
 * </ul>
 *
 * <p>Admin writes to {@code sec_authority} / {@code sec_permission} via
 * {@link com.vn.security.core.service.security.SecPermissionService} and
 * {@link com.vn.security.core.web.rest.admin.security.SecRoleAdminResource} already call
 * {@link #evictByAuthority(String)} programmatically — consumers do not need to call this service
 * for those flows.
 *
 * <p>{@link #warmUp(String)} is optional and only useful if the consumer wants to pre-populate
 * the cache on a successful login so the user's first request does not pay the provider lookup.
 */
@Service
public class UserAuthorityCacheService {

    private static final Logger LOG = LoggerFactory.getLogger(UserAuthorityCacheService.class);

    private final HazelcastInstance hazelcastInstance;
    private final CurrentUserAuthorityResolver authorityResolver;

    public UserAuthorityCacheService(HazelcastInstance hazelcastInstance, CurrentUserAuthorityResolver authorityResolver) {
        this.hazelcastInstance = hazelcastInstance;
        this.authorityResolver = authorityResolver;
    }

    /**
     * Removes the cached authority list for the given username. No-op if {@code username} is
     * blank or no entry exists.
     */
    public void evict(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        IMap<String, Collection<String>> cache = userCache();
        Collection<String> previous = cache.remove(username);
        if (previous != null && LOG.isDebugEnabled()) {
            LOG.debug("Evicted authority cache for username={} (had {} authorities)", username, previous.size());
        }
    }

    /**
     * Evicts cache entries scoped to the given authority name in both caches:
     * <ul>
     *   <li>{@code userAuthoritiesByUsername} — removes every entry whose resolved authority
     *       collection contains {@code authorityName}.</li>
     *   <li>{@code sec-permission-matrix} — removes every entry whose cache key (a sorted,
     *       pipe-joined role set, see
     *       {@link RequestPermissionSnapshot#toCacheKey(java.util.Collection)}) contains
     *       {@code authorityName} as one of the joined roles.</li>
     * </ul>
     *
     * <p>Use this when admin update or delete affects a single role and only users with that
     * role need fresh authorities on their next request. Prefer this over {@link #evictAll()}
     * to avoid mass repopulation by unrelated users.
     *
     * <p>No-op if {@code authorityName} is blank.
     */
    public void evictByAuthority(String authorityName) {
        if (authorityName == null || authorityName.isBlank()) {
            return;
        }

        IMap<String, Collection<String>> userCache = userCache();
        Set<String> usernamesToEvict = new HashSet<>();
        for (Map.Entry<String, Collection<String>> entry : userCache.entrySet()) {
            Collection<String> value = entry.getValue();
            if (value != null && value.contains(authorityName)) {
                usernamesToEvict.add(entry.getKey());
            }
        }
        for (String username : usernamesToEvict) {
            userCache.remove(username);
        }

        IMap<String, ?> matrixCache = matrixCache();
        Set<String> matrixKeysToEvict = new HashSet<>();
        for (Object rawKey : matrixCache.keySet()) {
            String key = String.valueOf(rawKey);
            for (String role : key.split("\\|", -1)) {
                if (role.equals(authorityName)) {
                    matrixKeysToEvict.add(key);
                    break;
                }
            }
        }
        for (String key : matrixKeysToEvict) {
            matrixCache.remove(key);
        }

        LOG.debug(
            "Evicted by authority={}: {} user cache entries, {} matrix cache entries",
            authorityName,
            usernamesToEvict.size(),
            matrixKeysToEvict.size()
        );
    }

    /**
     * Clears every entry in both authority caches. Use only for bulk operations whose effect on
     * the cache cannot be expressed as a finite set of {@link #evictByAuthority(String)} calls
     * (e.g. wholesale permission import, schema migration touching many roles at once).
     */
    public void evictAll() {
        IMap<String, Collection<String>> userCache = userCache();
        int userSize = userCache.size();
        userCache.clear();

        IMap<String, ?> matrixCache = matrixCache();
        int matrixSize = matrixCache.size();
        matrixCache.clear();

        LOG.debug("Evicted all authority caches: {} user entries, {} matrix entries", userSize, matrixSize);
    }

    /**
     * Eagerly resolves authorities for {@code username} so the value lands in the cache.
     *
     * <p>The resolver requires a populated {@link SecurityContextHolder} to look up authorities,
     * so this method is only useful when called from a request thread immediately after the
     * consumer has set the authentication (e.g. inside an {@code AuthenticationSuccessHandler}).
     * No-op outside that window.
     */
    public void warmUp(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !username.equals(authentication.getName())) {
            LOG.debug("warmUp({}) skipped — no matching authentication in SecurityContext", username);
            return;
        }
        authorityResolver.resolveAuthorities(authentication);
    }

    private IMap<String, Collection<String>> userCache() {
        return hazelcastInstance.getMap(AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME);
    }

    private IMap<String, ?> matrixCache() {
        return hazelcastInstance.getMap(RequestPermissionSnapshot.PERMISSION_MATRIX_CACHE);
    }
}
