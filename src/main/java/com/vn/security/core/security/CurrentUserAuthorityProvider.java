package com.vn.security.core.security;

import java.util.Collection;

/**
 * Consumer-provided adapter that resolves current role/authority names from an authenticated username.
 *
 * <p>Consumer applications own authentication and user-role assignment. The starter only needs
 * authority names to evaluate permissions and menus.
 */
public interface CurrentUserAuthorityProvider {
    Collection<String> getAuthorities(String username);
}
