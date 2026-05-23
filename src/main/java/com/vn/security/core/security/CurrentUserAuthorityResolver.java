package com.vn.security.core.security;

import java.util.Collection;
import org.springframework.security.core.Authentication;

/**
 * Resolves authority names for the current authentication.
 */
public interface CurrentUserAuthorityResolver {
    Collection<String> resolveAuthorities(Authentication authentication);
}
