package com.vn.security.core.security;

import java.util.Map;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.ClaimAccessor;

/**
 * Runtime security principal exposed through Spring Security's Authentication.
 *
 * <p>This is the starter-level contract. Consumer applications may implement this
 * directly or extend {@link AbstractSecurityPrincipal} without having to reuse the
 * starter's default user entity.
 */
public interface SecurityPrincipal extends UserDetails, ClaimAccessor {
    String getUserId();

    String getLogin();

    @Override
    Map<String, Object> getClaims();
}
