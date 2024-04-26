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
package org.niord.core.mail;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.util.concurrent.IsolationLevel;
import org.niord.core.cache.BaseCache;
import org.slf4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URL;

/**
 * Implements the mail attachment cache with a default timeout of 5 minutes.
 * <p>
 * If you compose a mime message with attachments by
 * setting "MimeBodyPart.setDataHandler(new DataHandler(url))",
 * then the url will actually be fetched 2-3 times during
 * the process of sending the mail by javamail!!
 * <p>
 * To alleviate this behavior, this {@code CachedUrlDataSource} can
 * be used to wrap the original data source.
 * The content is only loaded once and then cached in this cache.
 */
@ApplicationScoped
public class    MailAttachmentCache extends BaseCache<URL, CachedUrlData> {

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
