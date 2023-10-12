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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.infinispan.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.activation.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * If you compose a mime message with attachments by
 * setting "MimeBodyPart.setDataHandler(new DataHandler(url))",
 * then the url will actually be fetched 2-3 times during
 * the process of sending the mail by javamail!!
 * <p></p>
 * To alleviate this behavior, this DataSource can
 * be used to wrap the original data source.
 * The content is only loaded once and then cached. 
 */
public class CachedUrlDataSource implements DataSource {
	
	final static String DEFAULT_CONTENT_TYPE 	= "application/octet-stream";
	final static String DEFAULT_NAME 			= "unknown";
	final static Logger log = LoggerFactory.getLogger(CachedUrlDataSource.class);

	private Cache<URL, CachedUrlData> cache;
    private URL url;
    private CachedUrlData data;
    
    /**
     * Constructor
     * 
     * @param url the url of the attachment
     * @param cache the cache
     */
    public CachedUrlDataSource(URL url, Cache<URL, CachedUrlData> cache) {
    	this.url = url;
        this.cache = cache;
    }

    /**
     * Checks if the data is cached. Otherwise the URL data is loaded and cached
     * @return the URL data
     */
    protected synchronized CachedUrlData loadData() {
    	// Check if the attachment has been loaded already
    	if (data != null) {
    		return data;
    	}
    	
    	// Check if the attachment is stored in the global attachment cache
    	if (cache != null && cache.containsKey(url)) {
    		data = cache.get(url);
    		return data;
    	}

    	data = new CachedUrlData();
    	data.setUrl(url);
    	try {
    		// Resolve the name from the URL path
			String name = url.getPath();
			if (name.contains("/")) {
				name = name.substring(name.lastIndexOf("/") + 1);
			}
			data.setName(name);
    		    		
    		URLConnection urlc = url.openConnection();

    		// give it 15 seconds to respond
   	      	urlc.setReadTimeout(15*1000);

    		// Fetch the content type from the header
    		data.setContentType(urlc.getHeaderField("Content-Type"));
    		
    		// Load the content
            try (InputStream in = urlc.getInputStream()) {
                data.setContent(IOUtils.toByteArray(in));
            }
    	} catch (Exception ex) {
            // We don't really want to fail the mail if an attachment fails...
    	    log.warn("Silently failed loading mail attachment from " + url);
    	}
    	
    	// Cache the result
    	if (cache != null) {
    		cache.put(url, data);
    	}
    	
    	return data;
    }

    /** {@inheritDoc} */
    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(loadData().getContent());
    }

    /** {@inheritDoc} */
    @Override
    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** {@inheritDoc} */
    @Override
    public String getContentType() {
        return StringUtils.defaultIfBlank(loadData().getContentType(), DEFAULT_CONTENT_TYPE);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return StringUtils.defaultIfBlank(loadData().getName(), DEFAULT_NAME);
    }
}