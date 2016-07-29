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
package org.niord.core.mail;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.util.concurrent.IsolationLevel;
import org.niord.core.cache.BaseCache;
import org.slf4j.Logger;

import javax.ejb.Singleton;
import javax.inject.Inject;
import java.net.URL;

/**
 * Implements the mail attachment cache with a default timeout of 5 minutes.
 * <p>
 * If you compose a mime message with attachments by
 * setting "MimeBodyPart.setDataHandler(new DataHandler(url))",
 * then the url will actually be fetched 2-3 times during
 * the process of sending the mail by javamail!!
 * <p>
 * To alleviate this sickening behavior, this {@code CachedUrlDataSource} can
 * be used to wrap the original data source.
 * The content is only loaded once and then cached in this cache.
 */
@Singleton
public class MailAttachmentCache extends BaseCache<URL, CachedUrlData> {

    final static long LIFESPAN = 5 * 60 * 1000; // 5 minutes
    final static String CACHE_ID = "mailAttachmentCache";

    @Inject
    private Logger log;

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
