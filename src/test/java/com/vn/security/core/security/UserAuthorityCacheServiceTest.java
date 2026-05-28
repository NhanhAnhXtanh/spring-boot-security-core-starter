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
import com.vn.security.core.security.permission.RequestPermissionSnapshot;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

    @Mock
    @SuppressWarnings("rawtypes")
    private IMap matrixMap;

    private Map<String, Collection<String>> store;

    private Map<String, Object> matrixStore;

    private UserAuthorityCacheService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = new HashMap<>();
        matrixStore = new HashMap<>();
        lenient().when(hazelcastInstance.getMap(AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME)).thenReturn(cacheMap);
        lenient().when(hazelcastInstance.getMap(RequestPermissionSnapshot.PERMISSION_MATRIX_CACHE)).thenReturn(matrixMap);

        lenient().when(cacheMap.remove(anyString())).thenAnswer(inv -> store.remove(inv.getArgument(0, String.class)));
        lenient().when(cacheMap.size()).thenAnswer(inv -> store.size());
        lenient().when(cacheMap.entrySet()).thenAnswer(inv -> new HashSet<>(store.entrySet()));
        lenient().doAnswer(inv -> {
            store.clear();
            return null;
        }).when(cacheMap).clear();

        lenient().when(matrixMap.remove(anyString())).thenAnswer(inv -> matrixStore.remove(inv.getArgument(0, String.class)));
        lenient().when(matrixMap.size()).thenAnswer(inv -> matrixStore.size());
        lenient().when(matrixMap.keySet()).thenAnswer(inv -> new HashSet<>(matrixStore.keySet()));
        lenient().doAnswer(inv -> {
            matrixStore.clear();
            return null;
        }).when(matrixMap).clear();

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
        matrixStore.put("ROLE_USER", new Object());
        matrixStore.put("ROLE_ADMIN", new Object());

        service.evictAll();

        assertThat(store).isEmpty();
        assertThat(matrixStore).isEmpty();
        verify(cacheMap, times(1)).clear();
        verify(matrixMap, times(1)).clear();
    }

    @Test
    void evictByAuthorityRemovesUsersHavingThatAuthority() {
        store.put("alice", List.of("ROLE_USER"));
        store.put("bob", List.of("ROLE_ADMIN", "ROLE_USER"));
        store.put("carol", List.of("ROLE_ADMIN"));

        service.evictByAuthority("ROLE_USER");

        assertThat(store).containsOnlyKeys("carol");
    }

    @Test
    void evictByAuthorityRemovesMatrixKeysContainingThatAuthority() {
        matrixStore.put("ROLE_ADMIN|ROLE_USER", new Object());
        matrixStore.put("ROLE_USER", new Object());
        matrixStore.put("ROLE_MANAGER", new Object());

        service.evictByAuthority("ROLE_USER");

        assertThat(matrixStore).containsOnlyKeys("ROLE_MANAGER");
    }

    @Test
    void evictByAuthorityMatchesWholeRoleNotSubstring() {
        // ROLE_USER must not match ROLE_USERS or ROLE_ADMIN_USER — split on pipe is exact.
        store.put("alice", List.of("ROLE_USERS"));
        matrixStore.put("ROLE_USERS", new Object());
        matrixStore.put("ROLE_ADMIN_USER", new Object());

        service.evictByAuthority("ROLE_USER");

        assertThat(store).containsOnlyKeys("alice");
        assertThat(matrixStore).containsOnlyKeys("ROLE_USERS", "ROLE_ADMIN_USER");
    }

    @Test
    void evictByAuthorityIsNoopForBlankAuthority() {
        store.put("alice", List.of("ROLE_USER"));
        matrixStore.put("ROLE_USER", new Object());

        service.evictByAuthority("");
        service.evictByAuthority("   ");
        service.evictByAuthority(null);

        assertThat(store).containsOnlyKeys("alice");
        assertThat(matrixStore).containsOnlyKeys("ROLE_USER");
        verify(cacheMap, never()).remove(any());
        verify(matrixMap, never()).remove(any());
    }

    @Test
    void evictByAuthorityIsNoopWhenNoEntriesMatch() {
        store.put("alice", List.of("ROLE_USER"));
        matrixStore.put("ROLE_USER", new Object());

        service.evictByAuthority("ROLE_NONEXISTENT");

        assertThat(store).containsOnlyKeys("alice");
        assertThat(matrixStore).containsOnlyKeys("ROLE_USER");
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
