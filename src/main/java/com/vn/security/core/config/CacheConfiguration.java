package com.vn.security.core.config;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.vn.security.core.security.AuthorityCacheNames;
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
        config.addMapConfig(initializeUserAuthoritiesMapConfig());
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

    // Verified against hazelcast-spring 5.5.0 bytecode: HazelcastCacheManager.getCache(name)
    // wraps hazelcastInstance.getMap(name), and HazelcastCache.clear()/evict() delegate to
    // IMap.clear()/remove(). So consumer code touching the cache via Spring's CacheManager and
    // raw IMap access in DefaultCurrentUserAuthorityResolver / UserAuthorityCacheService operate
    // on the same underlying map — eviction is consistent across both call styles.
    //
    // No TTL: entries live until explicit eviction. Freshness contract:
    //   userAuthoritiesByUsername — evicted on consumer logout / user-role change /
    //       UserAuthorityCacheService.evictByAuthority on admin role-or-permission writes.
    //   sec-permission-matrix — evicted on UserAuthorityCacheService.evictByAuthority on admin
    //       role-or-permission writes. Logout does not touch matrix (matrix is keyed by sorted
    //       role-set, shared across all users with the same roles).
    // LRU + USED_HEAP_SIZE is the only ceiling, so neither map can grow unbounded if eviction
    // is missed.
    private MapConfig initializeUserAuthoritiesMapConfig() {
        MapConfig mapConfig = new MapConfig(AuthorityCacheNames.USER_AUTHORITIES_BY_USERNAME);
        mapConfig.setBackupCount(DEFAULT_BACKUP_COUNT);
        mapConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LRU);
        mapConfig.getEvictionConfig().setMaxSizePolicy(MaxSizePolicy.USED_HEAP_SIZE);
        return mapConfig;
    }

    private MapConfig initializePermissionMatrixMapConfig() {
        MapConfig mapConfig = new MapConfig(RequestPermissionSnapshot.PERMISSION_MATRIX_CACHE);
        mapConfig.setBackupCount(DEFAULT_BACKUP_COUNT);
        mapConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LRU);
        mapConfig.getEvictionConfig().setMaxSizePolicy(MaxSizePolicy.USED_HEAP_SIZE);
        return mapConfig;
    }
}
