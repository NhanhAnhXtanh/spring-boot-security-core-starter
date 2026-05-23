package com.vn.security.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security-core")
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();
    private final FetchPlans fetchPlans = new FetchPlans();
    private final Cors cors = new Cors();

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public FetchPlans getFetchPlans() {
        return fetchPlans;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Liquibase {

        private Boolean asyncStart = true;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }

    public static class FetchPlans {

        private String config = "classpath:fetch-plans.yml";

        public String getConfig() {
            return config;
        }

        public void setConfig(String config) {
            this.config = config;
        }
    }

    public static class Cors {

        private String allowedOrigins = "";
        private String allowedMethods = "*";
        private String allowedHeaders = "*";
        private boolean allowCredentials = true;
        private long maxAge = 1800;

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public String getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(String allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public String getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(long maxAge) {
            this.maxAge = maxAge;
        }
    }
}
