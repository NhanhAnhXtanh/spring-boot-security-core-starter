package com.vn.security.core.config;

import java.util.concurrent.Executor;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnClass(SpringLiquibase.class)
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiquibaseConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseConfiguration.class);

    private final Environment env;
    private final ApplicationProperties applicationProperties;

    @Value("${spring.liquibase.change-log:classpath:config/liquibase/master.xml}")
    private String changeLog;

    @Value("${spring.liquibase.default-schema:}")
    private String defaultSchema;

    @Value("${spring.liquibase.enabled:true}")
    private boolean liquibaseEnabled;

    public LiquibaseConfiguration(Environment env, ApplicationProperties applicationProperties) {
        this.env = env;
        this.applicationProperties = applicationProperties;
    }

    @Bean
    @ConditionalOnMissingBean(SpringLiquibase.class)
    public SpringLiquibase liquibase(
        DataSource dataSource,
        @Qualifier("taskExecutor") @Autowired(required = false) Executor executor
    ) {
        boolean async = Boolean.TRUE.equals(applicationProperties.getLiquibase().getAsyncStart())
            && executor != null;

        SpringLiquibase liquibase = async ? new AsyncSpringLiquibase(executor) : new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        if (defaultSchema != null && !defaultSchema.isBlank()) {
            liquibase.setDefaultSchema(defaultSchema);
        }

        if (env.matchesProfiles(AppConstants.SPRING_PROFILE_NO_LIQUIBASE)) {
            liquibase.setShouldRun(false);
        } else {
            liquibase.setShouldRun(liquibaseEnabled);
            LOG.debug("Configuring Liquibase");
        }
        return liquibase;
    }

    static class AsyncSpringLiquibase extends SpringLiquibase {

        private final Executor executor;

        AsyncSpringLiquibase(Executor executor) {
            this.executor = executor;
        }

        @Override
        public void afterPropertiesSet() {
            executor.execute(() -> {
                try {
                    LOG.debug("Starting Liquibase asynchronously");
                    super.afterPropertiesSet();
                    LOG.debug("Liquibase has started asynchronously");
                } catch (Exception e) {
                    LOG.error("Liquibase could not start asynchronously", e);
                }
            });
        }
    }
}
