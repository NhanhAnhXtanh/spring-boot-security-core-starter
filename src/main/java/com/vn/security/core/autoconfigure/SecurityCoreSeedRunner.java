package com.vn.security.core.autoconfigure;

import com.vn.security.core.config.ApplicationProperties;
import com.vn.security.core.domain.Authority;
import com.vn.security.core.domain.RoleType;
import com.vn.security.core.domain.User;
import com.vn.security.core.repository.AuthorityRepository;
import com.vn.security.core.repository.UserRepository;
import com.vn.security.core.security.AuthoritiesConstants;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default admin user on startup when {@code security-core.seed.enabled=true}.
 * Idempotent — skips if the configured username already exists.
 *
 * <p>Email property đã được bỏ cùng với email feature (bug #001).</p>
 *
 * <p><b>Never enable in production.</b> Default credentials are a known CVE vector.
 */
@Component
@ConditionalOnProperty(prefix = "security-core.seed", name = "enabled", havingValue = "true")
public class SecurityCoreSeedRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityCoreSeedRunner.class);

    private final ApplicationProperties properties;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    public SecurityCoreSeedRunner(
        ApplicationProperties properties,
        UserRepository userRepository,
        AuthorityRepository authorityRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ApplicationProperties.Seed seed = properties.getSeed();
        String login = seed.getUsername().toLowerCase();

        if (userRepository.findOneByLogin(login).isPresent()) {
            LOG.debug("Seed user '{}' already exists — skipping", login);
            return;
        }

        Authority adminAuthority = ensureAuthority(AuthoritiesConstants.ADMIN);
        Authority userAuthority = ensureAuthority(AuthoritiesConstants.USER);

        User user = new User();
        user.setLogin(login);
        user.setPassword(passwordEncoder.encode(seed.getPassword()));
        user.setFirstName("Default");
        user.setLastName("Admin");
        user.setActivated(true);
        user.setLangKey("en");

        Set<Authority> authorities = new HashSet<>();
        authorities.add(adminAuthority);
        authorities.add(userAuthority);
        user.setAuthorities(authorities);

        userRepository.save(user);

        LOG.warn("==================================================================");
        LOG.warn("SECURITY-CORE SEED: created default user '{}' with seeded password.", login);
        LOG.warn("This is INSECURE. Disable security-core.seed.enabled in production.");
        LOG.warn("==================================================================");
    }

    private Authority ensureAuthority(String name) {
        return authorityRepository
            .findById(name)
            .orElseGet(() -> {
                Authority authority = new Authority();
                authority.setName(name);
                authority.setDisplayName(name);
                authority.setType(RoleType.RESOURCE);
                return authorityRepository.save(authority);
            });
    }
}
