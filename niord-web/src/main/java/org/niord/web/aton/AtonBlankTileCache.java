package org.niord.web.aton;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.eviction.EvictionType;
import org.infinispan.util.concurrent.IsolationLevel;
import org.niord.core.cache.BaseCache;

import javax.enterprise.context.ApplicationScoped;

/**
 * Provides a cache of blank tiles.
 */
@ApplicationScoped
public class AtonBlankTileCache extends BaseCache<String, String> {

    final static long LIFESPAN = 6 * 60 * 60 * 1000;    // 6 hours
    final static long MAX_ENTRIES = 100000;             // at most 100.000 URL's

    final static String CACHE_ID = "atonTileCache";

    /** {@inheritDoc} */
    @Override
    public String getCacheId() {
        return CACHE_ID;
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
