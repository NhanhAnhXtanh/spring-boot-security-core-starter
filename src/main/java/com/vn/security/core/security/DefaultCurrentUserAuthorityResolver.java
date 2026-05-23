package com.vn.security.core.security;

import com.vn.security.core.domain.Authority;
import com.vn.security.core.repository.AuthorityRepository;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Default authority resolver for username-only authorization.
 *
 * <p>If the consumer provides {@link CurrentUserAuthorityProvider}, authorities are resolved
 * dynamically from {@link Authentication#getName()}. Otherwise this resolver falls back to
 * Spring Security's authorities on the authentication object.
 */
@Component
public class DefaultCurrentUserAuthorityResolver implements CurrentUserAuthorityResolver {

    private final ObjectProvider<CurrentUserAuthorityProvider> authorityProvider;
    private final AuthorityRepository authorityRepository;

    public DefaultCurrentUserAuthorityResolver(
        ObjectProvider<CurrentUserAuthorityProvider> authorityProvider,
        AuthorityRepository authorityRepository
    ) {
        this.authorityProvider = authorityProvider;
        this.authorityRepository = authorityRepository;
    }

    @Override
    public Collection<String> resolveAuthorities(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }

        CurrentUserAuthorityProvider provider = authorityProvider.getIfAvailable();
        Collection<String> authorityNames = provider == null
            ? authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
            : provider.getAuthorities(authentication.getName());

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
