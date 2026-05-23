package com.vn.security.core.security;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Loads the authenticated identity and maps it to a runtime security principal.
 *
 * <p>Consumer applications provide the implementation for their concrete user
 * table. The starter does not own a default user table.
 */
public interface SecurityIdentityService {
    SecurityPrincipal loadByLogin(String login) throws UsernameNotFoundException;
}
