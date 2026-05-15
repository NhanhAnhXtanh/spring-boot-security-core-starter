package com.vn.security.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoggingConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingConfiguration.class);

    public LoggingConfiguration(@Value("${spring.application.name}") String appName) {
        LOG.info("Logging configured for application: {}", appName);
    }
}
