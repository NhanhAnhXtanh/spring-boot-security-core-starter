package com.vn.security.core.security;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Consumer-facing helper for managing the username → authority cache backed by
 * {@link AuthorityCacheNames#USER_AUTHORITIES_BY_USERNAME}.
 *
 * <p>The starter does not own login/logout/user lifecycle. When consumers perform actions that
 * invalidate a user's cached authorities, they must call into this service so the next request
 * resolves fresh roles. Required call sites:
 *
 * <ul>
 *   <li>Logout — {@link #evict(String)} for the logged-out username (skip if policy is "cache
 *       across sessions for the same username").</li>
 *   <li>Disable / soft-delete user — {@link #evict(String)}.</li>
 *   <li>Change a single user's role membership — {@link #evict(String)}.</li>
 *   <li>Bulk role / group remap affecting many users — {@link #evictAll()}.</li>
 * </ul>
 *
 * <p>Admin writes to {@code sec_authority} / {@code sec_permission} already evict via
 * {@link com.vn.security.core.service.security.SecPermissionService} and
 * {@link com.vn.security.core.web.rest.admin.security.SecRoleAdminResource}, so consumers
 * do not need to call this service for those flows.
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
        IMap<String, Collection<String>> cache = cache();
        Collection<String> previous = cache.remove(username);
        if (previous != null && LOG.isDebugEnabled()) {
            LOG.debug("Evicted authority cache for username={} (had {} authorities)", username, previous.size());
        }
    }

    /**
     * Clears every entry in the username → authority cache. Use for bulk operations that
     * affect many users at once (e.g. wiping a role from all members of a group).
     */
    public void evictAll() {
        IMap<String, Collection<String>> cache = cache();
        int size = cache.size();
        cache.clear();
        LOG.debug("Evicted authority cache for all usernames (had {} entries)", size);
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

    private IMap<String, Collection<String>> cache() {
        return hazelcastInstance.getMap(AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME);
    }
}
