package org.niord.web.aton;

import org.niord.core.cache.BaseCache;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.eviction.EvictionType;
import org.infinispan.util.concurrent.IsolationLevel;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Provides a cache of blank tiles.
 */
@ApplicationScoped
public class AtonBlankTileCache extends BaseCache {

    final static long LIFESPAN = 6 * 60 * 60 * 1000;    // 6 hours
    final static long MAX_ENTRIES = 100000;             // at most 100.000 URL's

    final static String CACHE_ID = "atonTileCache";

    @Inject
    private Logger log;

    /**
     * Returns a reference to the settings cache
     * @return a reference to the settings cache
     */
    public Cache<String, String> getCache() {
        return cacheContainer.getCache(CACHE_ID);
    }

    /**
     * Clears the cache
     */
    @Override
    public  void clearCache() {
        log.info("Clearing cache " + CACHE_ID);
        getCache().clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Configuration createCacheConfiguration() {
        return new ConfigurationBuilder()
                .clustering().cacheMode(CacheMode.LOCAL)
                .locking().isolationLevel(IsolationLevel.REPEATABLE_READ)
                .eviction().type(EvictionType.COUNT).size(MAX_ENTRIES).strategy(EvictionStrategy.LRU)
                .expiration().lifespan(LIFESPAN)
                .build();
    }

}
