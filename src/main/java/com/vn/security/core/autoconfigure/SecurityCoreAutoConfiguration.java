package com.vn.security.core.autoconfigure;

import com.vn.security.core.config.ApplicationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EnableConfigurationProperties(ApplicationProperties.class)
@ComponentScan(basePackages = "com.vn.security.core")
@EntityScan(basePackages = "com.vn.security.core.domain")
@EnableJpaRepositories(basePackages = "com.vn.security.core.repository")
public class SecurityCoreAutoConfiguration {
}
