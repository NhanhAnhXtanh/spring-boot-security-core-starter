package com.vn.security.core.security;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.vn.security.core.domain.Authority;
import com.vn.security.core.repository.AuthorityRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Default authority resolver for username-only authorization.
 *
 * <p>If the consumer provides {@link CurrentUserAuthorityProvider}, authorities are resolved
 * dynamically from {@link Authentication#getName()}, validated against {@code sec_authority},
 * and cached per username so repeated requests skip both the provider call and the DB lookup.
 * Otherwise this resolver falls back to Spring Security's authorities on the authentication
 * object (still validated, but not cached because there is no stable per-user key).
 */
@Component
public class DefaultCurrentUserAuthorityResolver implements CurrentUserAuthorityResolver {

    private final ObjectProvider<CurrentUserAuthorityProvider> authorityProvider;
    private final AuthorityRepository authorityRepository;
    private final HazelcastInstance hazelcastInstance;

    public DefaultCurrentUserAuthorityResolver(
        ObjectProvider<CurrentUserAuthorityProvider> authorityProvider,
        AuthorityRepository authorityRepository,
        HazelcastInstance hazelcastInstance
    ) {
        this.authorityProvider = authorityProvider;
        this.authorityRepository = authorityRepository;
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public Collection<String> resolveAuthorities(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return List.of();
        }

        CurrentUserAuthorityProvider provider = authorityProvider.getIfAvailable();
        if (provider != null) {
            return resolveViaProvider(authentication.getName(), provider);
        }
        return validate(authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    private boolean isAnonymous(Authentication authentication) {
        return (
            authentication == null ||
            !authentication.isAuthenticated() ||
            authentication instanceof AnonymousAuthenticationToken ||
            authentication.getName() == null ||
            authentication.getName().isBlank() ||
            "anonymousUser".equals(authentication.getName()) ||
            authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(AuthoritiesConstants.ANONYMOUS::equals)
        );
    }

    /**
     * Loads authorities through the consumer provider and caches the validated result per username.
     * The cache stores the already-validated list so subsequent requests skip both the provider
     * call and the DB lookup. Cache is evicted on role/permission writes (see
     * {@link com.vn.security.core.service.security.SecPermissionService} and
     * {@link com.vn.security.core.web.rest.admin.security.SecRoleAdminResource}); consumers
     * editing user-role assignments must evict {@link AuthorityCacheNames#USER_AUTHORITIES_BY_USERNAME}.
     */
    private Collection<String> resolveViaProvider(String username, CurrentUserAuthorityProvider provider) {
        IMap<String, Collection<String>> cache = hazelcastInstance.getMap(AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME);
        Collection<String> cached = cache.get(username);
        if (cached != null) {
            return cached;
        }
        Collection<String> raw = provider.getAuthorities(username);
        Collection<String> validated = validate(raw);
        // Hazelcast IMap forbids null values; store an empty list to memoize "no authorities".
        cache.put(username, new ArrayList<>(validated));
        return validated;
    }

    /**
     * Drops blanks/duplicates and filters out authority names that are not backed by a row in
     * {@code sec_authority}. Returns a defensively-copied list safe to cache.
     */
    private Collection<String> validate(Collection<String> authorityNames) {
        if (authorityNames == null || authorityNames.isEmpty()) {
            return List.of();
        }
        Set<String> requestedNames = authorityNames.stream().filter(name -> name != null && !name.isBlank()).collect(Collectors.toSet());
        if (requestedNames.isEmpty()) {
            return List.of();
        }
        Set<String> validNames = authorityRepository
            .findAllById(requestedNames)
            .stream()
            .map(Authority::getName)
            .collect(Collectors.toSet());
        return requestedNames.stream().filter(validNames::contains).toList();
    }
}
