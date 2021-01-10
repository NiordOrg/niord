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
package org.niord.core.cache;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

/**
 * Base class for Infinispan caches
 */
@SuppressWarnings("unused")
public abstract class BaseCache<K, V> {

    @Inject
    private Logger log;

    protected EmbeddedCacheManager cacheContainer;

    /**
     * Starts the cache container
     */
    @PostConstruct
    public void initCacheContainer() {
        if (cacheContainer == null) {
            cacheContainer = new DefaultCacheManager();
            cacheContainer.defineConfiguration(getCacheId(), createCacheConfiguration());
            cacheContainer.start();

            log.info("Init cache container");
        }
    }

    /** Returns the cache ID */
    public abstract String getCacheId();


    /**
     * Returns a reference to the settings cache
     * @return a reference to the settings cache
     */
    public Cache<K, V> getCache() {
        return cacheContainer.getCache(getCacheId());
    }


    /**
     * Clears the cache
     */
    public  void clearCache() {
        log.info("Clearing cache " + getCacheId());
        getCache().clear();
    }


    /**
     * Must be implemented by sub-classes to define the local cache configuration
     * @return the local cache configuration
     */
    protected abstract Configuration createCacheConfiguration();

    /**
     * Stops the cache container
     */
    @PreDestroy
    public void destroyCacheContainer() {
        if (cacheContainer != null) {
            cacheContainer.stop();
            cacheContainer = null;
            log.info("Stopped cache container");
        }
    }
}
