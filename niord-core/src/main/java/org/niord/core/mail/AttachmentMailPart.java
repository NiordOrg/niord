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

import org.apache.commons.lang.StringUtils;
import org.infinispan.Cache;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.util.ByteArrayDataSource;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Encapsulates an attachment mail part
 */
@SuppressWarnings("unused")
public abstract class AttachmentMailPart implements Serializable {

    String name;

    /**
     * No-call constructor
     */
    private AttachmentMailPart() {
    }

    /**
     * Instantiates an attachment mail part from a file
     * @param file the attachment file
     */
    public static AttachmentMailPart fromFile(String file) throws MessagingException {
        return fromFile(file, null);
    }

    /**
     * Instantiates an attachment mail part from a file
     * @param file the attachment file
     * @param name the name of the attachment.
     */
    public static AttachmentMailPart fromFile(String file, String name)  throws MessagingException {
        return new FileAttachmentMailPart(file, name);
    }

    /**
     * Instantiates an attachment mail part from a URL
     * @param url the attachment URL
     */
    public static AttachmentMailPart fromUrl(String url) throws MessagingException {
        return fromUrl(url, null);
    }

    /**
     * Instantiates an attachment mail part from a URL
     * @param urlStr the attachment URL
     * @param name the name of the attachment.
     */
    public static AttachmentMailPart fromUrl(String urlStr, String name)  throws MessagingException {
        return new UrlAttachmentMailPart(urlStr, name);
    }

    /**
     * Instantiates an attachment mail part from a content byte array
     * @param content the content byte array
     * @param name the name of the attachment.
     */
    public static AttachmentMailPart fromContent(byte[] content, String name) throws MessagingException {
        return fromContent(content, null, name);
    }

    /**
     * Instantiates an attachment mail part from a content byte array
     * @param content the content byte array
     * @param contentType the content type
     * @param name the name of the attachment.
     */
    public static AttachmentMailPart fromContent(byte[] content, String contentType, String name)  throws MessagingException {
        return new ContentAttachmentMailPart(content, contentType, name);
    }

    /**
     * Returns a data-handler for this part
     * @return a data-handler for this part
     */
    public abstract DataHandler getDataHandler(Cache<URL, CachedUrlData> cache) throws MessagingException;

    public String getName() { return name; }


    /**
     * An attachment mail part based on a file
     */
    public static class FileAttachmentMailPart extends AttachmentMailPart {

        // File attachment
        String file;

        /**
         * Constructor
         * @param file the attachment file
         * @param name the name of the attachment.
         */
        private FileAttachmentMailPart(String name, String file) throws MessagingException {

            // Check that the file is valid
            Path path = Paths.get(file);
            if (!Files.isRegularFile(path)) {
                throw new MessagingException("Invalid file attachment: " + file);
            }
            this.file = file;

            // If name is not specified, compute it from the file
            if (StringUtils.isBlank(name)) {
                this.name = path.getFileName().toString();
            }
        }

        /**
         * Returns a data-handler for this part
         * @return a data-handler for this part
         */
        @Override
        public DataHandler getDataHandler(Cache<URL, CachedUrlData> cache) throws MessagingException {
            return new DataHandler(new FileDataSource(file));
        }
    }


    /**
     * An attachment mail part based on a URL to the attachment file
     */
    public static class UrlAttachmentMailPart extends AttachmentMailPart {

        // File attachment
        URL url;

        /** Constructor */
        private UrlAttachmentMailPart(String urlStr) throws MessagingException {
            this(null, urlStr);
        }

        /**
         * Constructor
         * @param urlStr the attachment URL
         * @param name the name of the attachment.
         */
        private UrlAttachmentMailPart(String name, String urlStr) throws MessagingException {
            // Check that the file is valid
            try {
                this.url = new URL(urlStr);
            } catch (MalformedURLException ex) {
                throw new MessagingException("Invalid url attachment: " + urlStr);
            }

            // If name is not specified, compute it from the URL
            if (StringUtils.isBlank(name)) {
                name = url.getPath();
                if (name.contains("/")) {
                    name = name.substring(name.lastIndexOf("/") + 1);
                }
                if (StringUtils.isBlank(name)) {
                    name = "unknown";
                }
            }
            this.name = name;
        }

        /**
         * Returns a data-handler for this part
         * @return a data-handler for this part
         */
        @Override
        public DataHandler getDataHandler(Cache<URL, CachedUrlData> cache) throws MessagingException {
            return new DataHandler(new CachedUrlDataSource(url, cache));
        }
    }


    /**
     * An attachment mail part based on byte array contents
     */
    public static class ContentAttachmentMailPart extends AttachmentMailPart {

        byte[] content;
        String contentType;

        /**
         * Constructor
         * @param content the content byte array
         * @param name the name of the attachment.
         */
        private ContentAttachmentMailPart(byte[] content, String name) throws MessagingException {
            this(content, null, name);
        }

        /**
         * Constructor
         * @param content the content byte array
         * @param contentType the content type
         * @param name the name of the attachment.
         */
        private ContentAttachmentMailPart(byte[] content, String contentType, String name) throws MessagingException {
            // Check that the content is a valid byte array
            if (content == null || content.length == 0) {
                throw new MessagingException("Invalid empty content byte array");
            }
            this.content = content;

            if (StringUtils.isBlank(contentType)) {
                contentType = "application/octet-stream";
            }
            this.contentType = contentType;

            if (StringUtils.isBlank(name)) {
                name = "unknown";
            }
            this.name = name;
        }

        /**
         * Returns a data-handler for this part
         * @return a data-handler for this part
         */
        @Override
        public DataHandler getDataHandler(Cache<URL, CachedUrlData> cache) throws MessagingException {
            return new DataHandler(new ByteArrayDataSource(content, contentType));
        }
    }

}
