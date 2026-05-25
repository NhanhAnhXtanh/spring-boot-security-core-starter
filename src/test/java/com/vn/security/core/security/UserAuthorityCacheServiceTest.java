package com.vn.security.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserAuthorityCacheServiceTest {

    @Mock
    private HazelcastInstance hazelcastInstance;

    @Mock
    private CurrentUserAuthorityResolver authorityResolver;

    @Mock
    @SuppressWarnings("rawtypes")
    private IMap cacheMap;

    private Map<String, Collection<String>> store;

    private UserAuthorityCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = new HashMap<>();
        lenient().when(hazelcastInstance.getMap(AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME)).thenReturn(cacheMap);
        lenient().when(cacheMap.remove(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0, String.class)));
        lenient().when(cacheMap.size()).thenAnswer(inv -> store.size());
        lenient().doAnswer(inv -> {
            store.clear();
            return null;
        }).when(cacheMap).clear();
        service = new UserAuthorityCacheService(hazelcastInstance, authorityResolver);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void evictRemovesEntryForUsername() {
        store.put("alice", List.of("ROLE_USER"));
        store.put("bob", List.of("ROLE_USER"));

        service.evict("alice");

        assertThat(store).containsOnlyKeys("bob");
    }

    @Test
    void evictIgnoresBlankUsername() {
        store.put("alice", List.of("ROLE_USER"));

        service.evict("");
        service.evict("   ");
        service.evict(null);

        // Map untouched; we never even ask Hazelcast for it.
        assertThat(store).containsOnlyKeys("alice");
        verify(cacheMap, never()).remove(any());
    }

    @Test
    void evictAllClearsEveryEntry() {
        store.put("alice", List.of("ROLE_USER"));
        store.put("bob", List.of("ROLE_ADMIN"));

        service.evictAll();

        assertThat(store).isEmpty();
        verify(cacheMap, times(1)).clear();
    }

    @Test
    void warmUpDelegatesToResolverWhenUsernameMatchesContext() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        service.warmUp("alice");

        verify(authorityResolver, times(1)).resolveAuthorities(auth);
    }

    @Test
    void warmUpIsNoopWhenContextUsernameDoesNotMatch() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        service.warmUp("bob");

        verify(authorityResolver, never()).resolveAuthorities(any());
    }

    @Test
    void warmUpIsNoopWhenSecurityContextIsEmpty() {
        SecurityContextHolder.clearContext();

        service.warmUp("alice");

        verify(authorityResolver, never()).resolveAuthorities(any());
    }

    @Test
    void warmUpIgnoresBlankUsername() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        service.warmUp("");
        service.warmUp(null);

        verify(authorityResolver, never()).resolveAuthorities(any());
    }
}
