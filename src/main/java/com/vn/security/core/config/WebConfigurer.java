package com.vn.security.core.config;

import jakarta.servlet.ServletContext;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebConfigurer implements ServletContextInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(WebConfigurer.class);

    private final Environment env;
    private final ApplicationProperties applicationProperties;

    public WebConfigurer(Environment env, ApplicationProperties applicationProperties) {
        this.env = env;
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void onStartup(ServletContext servletContext) {
        if (env.getActiveProfiles().length != 0) {
            LOG.info("Web application configuration, using profiles: {}", (Object[]) env.getActiveProfiles());
        }
        LOG.info("Web application fully configured");
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        ApplicationProperties.Cors corsProps = applicationProperties.getCors();
        String allowedOrigins = corsProps.getAllowedOrigins();

        if (StringUtils.hasText(allowedOrigins)) {
            LOG.debug("Registering CORS filter");
            CorsConfiguration config = new CorsConfiguration();
            List<String> origins = Arrays.asList(allowedOrigins.split(","));
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of(corsProps.getAllowedMethods().equals("*")
                ? new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"}
                : corsProps.getAllowedMethods().split(",")));
            if (corsProps.getAllowedHeaders().equals("*")) {
                config.addAllowedHeader("*");
            } else {
                config.setAllowedHeaders(Arrays.asList(corsProps.getAllowedHeaders().split(",")));
            }
            config.setAllowCredentials(corsProps.isAllowCredentials());
            config.setMaxAge(corsProps.getMaxAge());

            source.registerCorsConfiguration("/api/**", config);
            source.registerCorsConfiguration("/management/**", config);
            source.registerCorsConfiguration("/v3/api-docs", config);
            source.registerCorsConfiguration("/swagger-ui/**", config);
        }
        return new CorsFilter(source);
    }
}
