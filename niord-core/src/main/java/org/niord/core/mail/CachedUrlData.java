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

import java.io.Serializable;
import java.net.URL;

/**
 * Used to cache the data associated with a URL used for mail attachments
 */
public class CachedUrlData implements Serializable {

	URL url;
	String name;
	String contentType;
	byte[] content;

    /*************************/
    /** Getters and Setters **/
    /*************************/

	public URL getUrl() { return url; }
	public void setUrl(URL url) { this.url = url; }
	
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	
	public String getContentType() { return contentType; }
	public void setContentType(String contentType) { this.contentType = contentType; }
	
	public byte[] getContent() { return content; }
	public void setContent(byte[] content) { this.content = content; }
}
