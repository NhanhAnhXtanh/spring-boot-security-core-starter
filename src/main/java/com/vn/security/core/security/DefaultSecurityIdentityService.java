package com.vn.security.core.security;

import com.vn.security.core.domain.Authority;
import com.vn.security.core.domain.User;
import com.vn.security.core.repository.UserRepository;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default identity adapter backed by the starter's {@code sec_user} table.
 */
@Component
@ConditionalOnMissingBean(SecurityIdentityService.class)
public class DefaultSecurityIdentityService implements SecurityIdentityService {

    private final UserRepository userRepository;

    public DefaultSecurityIdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityPrincipal loadByLogin(String login) throws UsernameNotFoundException {
        String lowercaseLogin = login.toLowerCase(Locale.ENGLISH);
        return userRepository
            .findOneWithAuthoritiesByLogin(lowercaseLogin)
            .map(user -> toPrincipal(lowercaseLogin, user))
            .orElseThrow(() -> new UsernameNotFoundException("User " + lowercaseLogin + " was not found in the database"));
    }

    private SecurityPrincipal toPrincipal(String lowercaseLogin, User user) {
        if (!user.isActivated()) {
            throw new UserNotActivatedException("User " + lowercaseLogin + " was not activated");
        }
        String userId = user.getId() == null ? null : user.getId().toString();
        Map<String, Object> claims = userId == null ? Map.of() : Map.of(SecurityUtils.USER_ID_CLAIM, userId);
        return new DefaultSecurityPrincipal(
            userId,
            user.getLogin(),
            user.getPassword(),
            user.isActivated(),
            user.getAuthorities().stream().map(Authority::getName).map(SimpleGrantedAuthority::new).toList(),
            claims
        );
    }
}
