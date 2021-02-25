/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.web.aton;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.eviction.EvictionStrategy;
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
                .memory().maxCount(MAX_ENTRIES).whenFull(EvictionStrategy.REMOVE)
                .expiration().lifespan(LIFESPAN)
                .build();
    }

}
