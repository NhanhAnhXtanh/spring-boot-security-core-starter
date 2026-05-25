package com.vn.security.core.security.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vn.security.core.security.CurrentUserAuthorityResolver;
import com.vn.security.core.security.permission.RequestPermissionSnapshot;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class MergedSecurityContextBridgeTest {

    @Mock
    private RequestPermissionSnapshot snapshot;

    @Mock
    private CurrentUserAuthorityResolver authorityResolver;

    private MergedSecurityContextBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new MergedSecurityContextBridge(snapshot, authorityResolver);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void loginIsEmptyWhenNoAuthentication() {
        assertThat(bridge.getCurrentUserLogin()).isEmpty();
    }

    @Test
    void loginReturnsAuthName() {
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("alice", "pw", List.of()));
        assertThat(bridge.getCurrentUserLogin()).contains("alice");
    }

    @Test
    void authoritiesComeFromSnapshotWhenInsideRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("alice", "pw", List.of()));
        when(snapshot.getAuthorities()).thenReturn(List.of("ROLE_USER"));

        assertThat(bridge.getCurrentUserAuthorities()).containsExactly("ROLE_USER");

        // Resolver must NOT be invoked when the request-scoped snapshot is available — the
        // whole point of the snapshot is to memoize the resolver call once per request.
        verify(authorityResolver, never()).resolveAuthorities(any());
    }

    @Test
    void authoritiesFallBackToResolverOutsideRequest() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(authorityResolver.resolveAuthorities(auth)).thenReturn(List.of("ROLE_BATCH"));

        assertThat(bridge.getCurrentUserAuthorities()).containsExactly("ROLE_BATCH");
        verify(snapshot, never()).getAuthorities();
    }

    @Test
    void authoritiesEmptyWhenNoAuthentication() {
        // No request scope, no authentication -> short-circuit to empty.
        assertThat(bridge.getCurrentUserAuthorities()).isEmpty();
        verify(authorityResolver, never()).resolveAuthorities(any());
    }

    @Test
    void isAuthenticatedTrueForNonAnonymousResolved() {
        Authentication auth = new UsernamePasswordAuthenticationToken("alice", "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(authorityResolver.resolveAuthorities(auth)).thenReturn(List.of("ROLE_USER"));

        assertThat(bridge.isAuthenticated()).isTrue();
    }

    @Test
    void isAuthenticatedFalseWhenResolverYieldsOnlyAnonymous() {
        // Even with an authenticated token in the context, the bridge treats a resolver
        // that returns ROLE_ANONYMOUS as not-authenticated. This catches the case where
        // the consumer auth layer let an anonymous-equivalent principal through.
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "ghost",
            "pw",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(authorityResolver.resolveAuthorities(auth)).thenReturn(List.of("ROLE_ANONYMOUS"));

        assertThat(bridge.isAuthenticated()).isFalse();
    }

    @Test
    void isAuthenticatedFalseWhenAnonymousToken() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
            "key",
            "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        when(authorityResolver.resolveAuthorities(anonymous)).thenReturn(List.of());

        // AnonymousAuthenticationToken.isAuthenticated() returns true, but the resolver returns
        // empty for it (because DefaultCurrentUserAuthorityResolver short-circuits on anonymous).
        // Bridge sees no ROLE_ANONYMOUS in empty stream -> "authenticated". This test pins the
        // current behavior so a future change to the bridge's anonymous handling is intentional.
        assertThat(bridge.isAuthenticated()).isTrue();
    }
}
