package com.vn.security.core.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.vn.security.core.security.UserCacheNames;
import com.vn.security.core.security.permission.RequestPermissionSnapshot;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@EnableCaching
@ConditionalOnClass(HazelcastInstance.class)
@ConditionalOnProperty(prefix = "security-core.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfiguration.class);
    private static final int DEFAULT_TTL_SECONDS = 3600;
    private static final int DEFAULT_BACKUP_COUNT = 1;
    /**
     * Short TTL safety net for {@code usersByLogin}. Consumer identity adapters
     * can use this cache name and rely on starter permission/role writes to evict it.
     */
    private static final int USERS_BY_LOGIN_TTL_SECONDS = 60;

    private final Environment env;

    public CacheConfiguration(Environment env) {
        this.env = env;
    }

    @PreDestroy
    public void destroy() {
        LOG.info("Closing Cache Manager");
        Hazelcast.shutdownAll();
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(HazelcastInstance hazelcastInstance) {
        LOG.debug("Starting HazelcastCacheManager");
        return new com.hazelcast.spring.cache.HazelcastCacheManager(hazelcastInstance);
    }

    @Bean
    @ConditionalOnMissingBean(HazelcastInstance.class)
    public HazelcastInstance hazelcastInstance() {
        LOG.debug("Configuring Hazelcast");
        HazelcastInstance hazelCastInstance = Hazelcast.getHazelcastInstanceByName("security-core");
        if (hazelCastInstance != null) {
            LOG.debug("Hazelcast already initialized");
            return hazelCastInstance;
        }
        Config config = new Config();
        config.setInstanceName("security-core");
        config.getNetworkConfig().setPort(5701);
        config.getNetworkConfig().setPortAutoIncrement(true);

        ManagementCenterConfig mcConfig = new ManagementCenterConfig();
        if (env.acceptsProfiles(Profiles.of(AppConstants.SPRING_PROFILE_DEVELOPMENT))) {
            System.setProperty("hazelcast.local.localAddress", "0.0.0.0");
            config.getNetworkConfig().getJoin().getAwsConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
            mcConfig.setScriptingEnabled(true)
                    .setConsoleEnabled(true)
                    .setDataAccessEnabled(true);
        }
        config.setManagementCenterConfig(mcConfig);
        config.addMapConfig(initializeDefaultMapConfig());
        config.addMapConfig(initializeDomainMapConfig());
        config.addMapConfig(initializePermissionMatrixMapConfig());
        config.addMapConfig(initializeUsersByLoginMapConfig());
        return Hazelcast.newHazelcastInstance(config);
    }

    private MapConfig initializeDefaultMapConfig() {
        MapConfig mapConfig = new MapConfig("default");
        mapConfig.setBackupCount(DEFAULT_BACKUP_COUNT);
        mapConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LRU);
        mapConfig.getEvictionConfig().setMaxSizePolicy(MaxSizePolicy.USED_HEAP_SIZE);
        return mapConfig;
    }

    private MapConfig initializeDomainMapConfig() {
        MapConfig mapConfig = new MapConfig("com.vn.security.core.domain.*");
        mapConfig.setTimeToLiveSeconds(DEFAULT_TTL_SECONDS);
        return mapConfig;
    }

    private MapConfig initializeUsersByLoginMapConfig() {
        MapConfig mapConfig = new MapConfig(UserCacheNames.USERS_BY_LOGIN);
        mapConfig.setTimeToLiveSeconds(USERS_BY_LOGIN_TTL_SECONDS);
        mapConfig.setBackupCount(DEFAULT_BACKUP_COUNT);
        mapConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LRU);
        mapConfig.getEvictionConfig().setMaxSizePolicy(MaxSizePolicy.USED_HEAP_SIZE);
        return mapConfig;
    }

    private MapConfig initializePermissionMatrixMapConfig() {
        MapConfig mapConfig = new MapConfig(RequestPermissionSnapshot.PERMISSION_MATRIX_CACHE);
        mapConfig.setTimeToLiveSeconds(DEFAULT_TTL_SECONDS);
        mapConfig.setBackupCount(DEFAULT_BACKUP_COUNT);
        mapConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LRU);
        mapConfig.getEvictionConfig().setMaxSizePolicy(MaxSizePolicy.USED_HEAP_SIZE);
        return mapConfig;
    }
}
