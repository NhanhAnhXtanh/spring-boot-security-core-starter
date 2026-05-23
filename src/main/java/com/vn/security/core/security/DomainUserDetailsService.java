package com.vn.security.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Authenticate a user through the configured identity adapter.
 *
 * <p>Consumer applications must provide a {@link SecurityIdentityService}
 * that loads their user table and maps it to a {@link SecurityPrincipal}.
 */
@Component("userDetailsService")
public class DomainUserDetailsService implements UserDetailsService {

    private static final Logger LOG = LoggerFactory.getLogger(DomainUserDetailsService.class);

    private final SecurityIdentityService securityIdentityService;

    public DomainUserDetailsService(SecurityIdentityService securityIdentityService) {
        this.securityIdentityService = securityIdentityService;
    }

    @Override
    public UserDetails loadUserByUsername(final String login) {
        LOG.debug("Authenticating {}", login);
        return securityIdentityService.loadByLogin(login);
    }
}
