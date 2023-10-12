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

import org.infinispan.Cache;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * An inline (related) mail part for a resource associated with a HTML message body
 */
public class InlineMailPart implements Serializable {

	String url;
	String contentId;
	
	/**
	 * Constructor
	 * @param contentId the id of the related inline body part
	 * @param url the url of the part
	 */
	public InlineMailPart(String contentId, String url) {
		this.contentId = contentId;
		this.url = url;
	}
	
	/**
	 * Returns a data-handler for this part
	 * @return a data-handler for this part
	 */
	public DataHandler getDataHandler(Cache<URL, CachedUrlData> cache) throws MessagingException {
		try {
			return new DataHandler(new CachedUrlDataSource(new URL(url), cache));
		} catch (MalformedURLException ex) {
			throw new MessagingException("Invalid url " + url);
		}
	}

	public String getUrl() { return url; }

	public String getContentId() { return contentId; }

	/**
	 * Returns a string representation of this part
	 * @return a string representation of this part
	 */
	public String toString() {
		return "[contentId=" + contentId + ", url=" + url + "]";
	}
}
