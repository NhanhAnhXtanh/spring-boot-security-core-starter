package com.vn.security.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.vn.security.core.domain.Authority;
import com.vn.security.core.repository.AuthorityRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class DefaultCurrentUserAuthorityResolverTest {

    @Mock
    private ObjectProvider<CurrentUserAuthorityProvider> providerObjectProvider;

    @Mock
    private AuthorityRepository authorityRepository;

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    @SuppressWarnings("rawtypes")
    private IMap cacheMap;

    /** Backing store the mocked IMap reads from / writes to, so cache-hit assertions work. */
    private Map<String, Collection<String>> store;

    private DefaultCurrentUserAuthorityResolver resolver;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = new HashMap<>();
        lenient().when(hazelcastInstance.getMap(AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME)).thenReturn(cacheMap);
        lenient().when(cacheMap.get(anyString())).thenAnswer(inv -> store.get(inv.getArgument(0, String.class)));
        lenient().when(cacheMap.put(anyString(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            Collection<String> value = inv.getArgument(1, Collection.class);
            return store.put(key, value);
        });
        resolver = new DefaultCurrentUserAuthorityResolver(providerObjectProvider, authorityRepository, hazelcastInstance);
    }

    @Test
    void returnsEmptyForNullAuthentication() {
        assertThat(resolver.resolveAuthorities(null)).isEmpty();
    }

    @Test
    void returnsEmptyForAnonymousToken() {
        Authentication anonymous = new AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            List.of(new SimpleGrantedAuthority(AuthoritiesConstants.ANONYMOUS))
        );
        assertThat(resolver.resolveAuthorities(anonymous)).isEmpty();
    }

    @Test
    void returnsEmptyWhenAuthenticationNameIsBlank() {
        Authentication auth = new UsernamePasswordAuthenticationToken("   ", "pw", List.of());
        assertThat(resolver.resolveAuthorities(auth)).isEmpty();
    }

    @Test
    void fallsBackToSpringAuthoritiesWhenNoProvider() {
        when(providerObjectProvider.getIfAvailable()).thenReturn(null);
        when(authorityRepository.findAllById(any())).thenReturn(List.of(authority("ROLE_USER")));

        Authentication auth = new UsernamePasswordAuthenticationToken(
            "alice",
            "pw",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        assertThat(resolver.resolveAuthorities(auth)).containsExactly("ROLE_USER");
        // No-provider path must not touch the per-username cache (no stable key).
        assertThat(store).isEmpty();
    }

    @Test
    void filtersOutPhantomAuthoritiesNotBackedByDb() {
        when(providerObjectProvider.getIfAvailable()).thenReturn(null);
        // DB only knows ROLE_USER; ROLE_GHOST is a phantom claim and must be dropped.
        when(authorityRepository.findAllById(any())).thenReturn(List.of(authority("ROLE_USER")));

        Authentication auth = new UsernamePasswordAuthenticationToken(
            "alice",
            "pw",
            List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_GHOST"))
        );

        assertThat(resolver.resolveAuthorities(auth)).containsExactly("ROLE_USER");
    }

    @Test
    void usesProviderAndCachesValidatedResultPerUsername() {
        CurrentUserAuthorityProvider provider = org.mockito.Mockito.mock(CurrentUserAuthorityProvider.class);
        when(providerObjectProvider.getIfAvailable()).thenReturn(provider);
        when(provider.getAuthorities("alice")).thenReturn(List.of("ROLE_USER", "ROLE_GHOST"));
        when(authorityRepository.findAllById(any())).thenReturn(List.of(authority("ROLE_USER")));

        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "pw", List.of());

        assertThat(resolver.resolveAuthorities(auth)).containsExactly("ROLE_USER");
        assertThat(resolver.resolveAuthorities(auth)).containsExactly("ROLE_USER");

        // Cache hit on second call: provider + DB must each run only once.
        verify(provider, times(1)).getAuthorities("alice");
        verify(authorityRepository, times(1)).findAllById(any());
        assertThat(store).containsKey("alice");
    }

    @Test
    void handlesNullFromProviderWithoutThrowing() {
        CurrentUserAuthorityProvider provider = org.mockito.Mockito.mock(CurrentUserAuthorityProvider.class);
        when(providerObjectProvider.getIfAvailable()).thenReturn(provider);
        when(provider.getAuthorities("bob")).thenReturn(null);

        Authentication auth = new UsernamePasswordAuthenticationToken("bob", "pw", List.of());

        assertThat(resolver.resolveAuthorities(auth)).isEmpty();
        // DB validation is skipped when the provider returns nothing.
        verify(authorityRepository, never()).findAllById(any());
        // Empty result must still be memoized so the next request does not re-call the provider.
        assertThat(store).containsEntry("bob", new ArrayList<>());
    }

    @Test
    void handlesEmptyFromProvider() {
        CurrentUserAuthorityProvider provider = org.mockito.Mockito.mock(CurrentUserAuthorityProvider.class);
        when(providerObjectProvider.getIfAvailable()).thenReturn(provider);
        when(provider.getAuthorities("eve")).thenReturn(List.of());

        Authentication auth = new UsernamePasswordAuthenticationToken("eve", "pw", List.of());

        assertThat(resolver.resolveAuthorities(auth)).isEmpty();
        verify(authorityRepository, never()).findAllById(any());
    }

    @Test
    void dropsBlankAuthorityNames() {
        CurrentUserAuthorityProvider provider = org.mockito.Mockito.mock(CurrentUserAuthorityProvider.class);
        when(providerObjectProvider.getIfAvailable()).thenReturn(provider);
        when(provider.getAuthorities("carol")).thenReturn(Arrays.asList("ROLE_USER", "", "   ", null));
        when(authorityRepository.findAllById(any())).thenReturn(List.of(authority("ROLE_USER")));

        Authentication auth = new UsernamePasswordAuthenticationToken("carol", "pw", List.of());

        assertThat(resolver.resolveAuthorities(auth)).containsExactly("ROLE_USER");
    }

    @Test
    void cacheHitDoesNotInvokeProviderOrDb() {
        CurrentUserAuthorityProvider provider = org.mockito.Mockito.mock(CurrentUserAuthorityProvider.class);
        when(providerObjectProvider.getIfAvailable()).thenReturn(provider);
        store.put("dan", List.of("ROLE_USER"));

        Authentication auth = new UsernamePasswordAuthenticationToken("dan", "pw", List.of());

        assertThat(resolver.resolveAuthorities(auth)).containsExactly("ROLE_USER");
        verify(provider, never()).getAuthorities(anyString());
        verify(authorityRepository, never()).findAllById(any());
    }

    private static Authority authority(String name) {
        Authority a = new Authority();
        a.setName(name);
        return a;
    }
}
