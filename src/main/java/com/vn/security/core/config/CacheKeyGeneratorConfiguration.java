package com.vn.security.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Optional;

@Configuration
public class CacheKeyGeneratorConfiguration {

    private GitProperties gitProperties;
    private BuildProperties buildProperties;

    @Autowired(required = false)
    public void setGitProperties(GitProperties gitProperties) {
        this.gitProperties = gitProperties;
    }

    @Autowired(required = false)
    public void setBuildProperties(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @Bean
    public KeyGenerator keyGenerator() {
        String prefix = Optional.ofNullable(buildProperties)
            .map(b -> b.getVersion() + "-")
            .orElseGet(() -> Optional.ofNullable(gitProperties)
                .map(g -> g.getShortCommitId() + "-")
                .orElse(""));
        return (target, method, params) -> {
            StringBuilder key = new StringBuilder(prefix);
            key.append(target.getClass().getSimpleName()).append(".").append(method.getName());
            for (Object p : params) key.append(".").append(p);
            return key.toString();
        };
    }
}
