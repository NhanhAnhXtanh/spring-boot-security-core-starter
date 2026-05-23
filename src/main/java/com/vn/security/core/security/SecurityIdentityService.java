package com.vn.security.core.security;

import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Loads the authenticated identity and maps it to a runtime security principal.
 *
 * <p>Consumer applications can provide their own implementation to use an
 * existing user table instead of the starter's default {@code sec_user} table.
 */
public interface SecurityIdentityService {
    SecurityPrincipal loadByLogin(String login) throws UsernameNotFoundException;
}
