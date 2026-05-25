package com.vn.security.core.security.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RequestPermissionSnapshotTest {

    @Test
    void cacheKeyIsOrderIndependent() {
        String a = RequestPermissionSnapshot.toCacheKey(List.of("ROLE_USER", "ROLE_ADMIN"));
        String b = RequestPermissionSnapshot.toCacheKey(List.of("ROLE_ADMIN", "ROLE_USER"));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void cacheKeyDeduplicates() {
        String a = RequestPermissionSnapshot.toCacheKey(List.of("ROLE_USER", "ROLE_USER", "ROLE_ADMIN"));
        String b = RequestPermissionSnapshot.toCacheKey(List.of("ROLE_USER", "ROLE_ADMIN"));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void cacheKeyUsesExplicitPipeDelimiter() {
        // Pins the format: "ROLE_ADMIN|ROLE_USER". If this changes, every cached entry
        // becomes stale on deploy — TTL eventually fixes it, but verify the change is intentional.
        String key = RequestPermissionSnapshot.toCacheKey(List.of("ROLE_USER", "ROLE_ADMIN"));
        assertThat(key).isEqualTo("ROLE_ADMIN|ROLE_USER");
    }

    @Test
    void cacheKeyForEmptyInputIsEmptyString() {
        assertThat(RequestPermissionSnapshot.toCacheKey(List.of())).isEmpty();
    }

    @Test
    void cacheKeyForSingleAuthorityHasNoDelimiter() {
        assertThat(RequestPermissionSnapshot.toCacheKey(List.of("ROLE_USER"))).isEqualTo("ROLE_USER");
    }

    @Test
    void isRequestScopeActiveReturnsFalseOutsideRequest() {
        // Sanity: outside a Spring web request, the snapshot's static guard must return false
        // so non-web callers (batch / tests) fall through to the resolver instead of trying
        // to use the request-scoped proxy.
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        assertThat(RequestPermissionSnapshot.isRequestScopeActive()).isFalse();
    }

    @Test
    void isRequestScopeActiveReturnsTrueWhenAttributesBound() {
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(
                new org.springframework.mock.web.MockHttpServletRequest()
            )
        );
        try {
            assertThat(RequestPermissionSnapshot.isRequestScopeActive()).isTrue();
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }
}
