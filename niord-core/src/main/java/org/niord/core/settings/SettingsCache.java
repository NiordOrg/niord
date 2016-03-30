/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.settings;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.util.concurrent.IsolationLevel;
import org.niord.core.cache.BaseCache;
import org.niord.core.cache.CacheElement;

import javax.enterprise.context.ApplicationScoped;

/**
 * Implements the settings cache with a default timeout of 1 minute.
 *
 * NB: We cannot cache null, so use a CacheElement value wrapper.
 */
@ApplicationScoped
public class SettingsCache extends BaseCache<String, CacheElement<Object>> {

    final static long LIFESPAN      = 3 * 60 * 1000;    // 3 minutes
    final static String CACHE_ID    = "settingsCache";

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
                .expiration().lifespan(LIFESPAN)
                .build();
    }

}
