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

package org.niord.core.repo;

import io.quarkus.arc.Lock;
import io.quarkus.runtime.StartupEvent;
import org.apache.commons.io.FilenameUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Enlists the supported file types and maps them to icons
 */
@ApplicationScoped
@Lock(Lock.Type.READ)
@SuppressWarnings("unused")
public class FileTypes {

    static final String GENERIC_ICON 		= "generic";
    static final String FILE_TYPE_FOLDER 	= "img/filetypes";
    static final String OOD                 = "application/vnd.openxmlformats-officedocument";

    Map<String, Set<ContentType>> mimeTypeLookup = new HashMap<>();
    Map<String, ContentType> fileExtensionLookup = new HashMap<>();
    ContentType genericType = new ContentType(null, null);

    /**
     * Defines the supported content types
     */
    void init(@Observes StartupEvent ev) {
        addConentType("application/msword", 			    "docx", 	"doc", "dot");
        addConentType("application/pdf", 			    	"pdf", 		"pdf");
        addConentType("application/vnd.ms-excel",   		"xlsx", 	"xls", "xlt");
        addConentType("application/vnd.ms-powerpoint", 	    "pptx", 	"ppt", "pot");
        addConentType("application/zip", 			    	"zip", 		"zip");
        addConentType("audio/mpeg", 			    		"mp3", 		"mp3");
        addConentType("image/bmp", 						    "bmp", 		"bmp");
        addConentType("image/gif", 					    	"gif", 		"gif");
        addConentType("image/png", 					    	"png", 		"png");
        addConentType("image/jpeg", 			    		"jpeg",		"jpg", "jpeg");
        addConentType("image/tiff", 		    			"tiff", 	"tif", "tiff");
        addConentType("text/html", 		    				"html",		"html", "htm");
        addConentType("text/plain",      					"text", 	"txt");
        addConentType("text/richtext", 				    	"text", 	"rtf");
        addConentType("video/mpeg", 			    		"mpeg", 	"mpg", "mpeg");
        addConentType("video/quicktime", 	    			"mpeg", 	"mov");
        addConentType("video/x-msvideo",     				"mpeg", 	"avi");
        addConentType("video/mp4", 			    		    "mpeg", 	"mp4");
        addConentType("video/m4v", 			    		    "mpeg", 	"m4v");
        addConentType(OOD + ".wordprocessingml.document",   "docx",		"docx");
        addConentType(OOD + ".wordprocessingml.template", 	"docx",		"dotx");
        addConentType(OOD + ".spreadsheetml.sheet", 		"xslx",		"xslx");
        addConentType(OOD + ".spreadsheetml.template",		"xslx",		"xltx");
        addConentType(OOD + ".presentationml.presentation", "pptx",		"pptx");
        addConentType(OOD + ".presentationml.template", 	"pptx",		"potx");
        addConentType(OOD + ".presentationml.slideshow", 	"pptx",		"ppsx");
        // Etc...
    }

    /**
     * Adds a new content type
     * @param mimeType the mime type
     * @param icon the associated icon
     * @param fileExtensions the file extensions
     */
    private void addConentType(String mimeType, String icon, String... fileExtensions) {
        ContentType type = new ContentType(mimeType, icon, fileExtensions);

        // Update look-up maps
        Set<ContentType> m = mimeTypeLookup.computeIfAbsent(mimeType, k -> new HashSet<>());
        m.add(type);

        if (fileExtensions != null) {
            for (String ext : fileExtensions) {
                fileExtensionLookup.put(ext, type);
            }
        }
    }

    /**
     * Returns the content type of the file, or null if unknown
     * @param path the file to check
     * @return the content type of the file, or null if unknown
     */
    public String getContentType(Path path) {
        try {
            // This is just patently ridiculous ... probing the file system for a content type on a Mac OS X
            // still does not work properly :-(
            // return Files.probeContentType(path);
            // return new MimetypesFileTypeMap().getContentType(path.toFile());
            String ext = FilenameUtils.getExtension(path.toString());
            return fileExtensionLookup.get(ext.toLowerCase()).getMimeType();
        } catch (Exception e) {
            // Unknown type
            return null;
        }
    }

    /**
     * Returns the content type of the file, or null if unknown
     * @param file the file to check
     * @return the content type of the file, or null if unknown
     */
    public String getContentType(File file) {
        return getContentType(file.toPath());
    }

    /**
     * Returns the associated content type if supported
     * @param path the file to check
     * @return the associated content type if supported
     */
    private ContentType getSupportedContentType(Path path) {
        if (path == null) {
            return null;
        }

        String type = getContentType(path);
        if (type != null) {
            Set<ContentType> supportedTypes = mimeTypeLookup.get(type);
            if (supportedTypes != null) {
                // Take the first match
                return supportedTypes.iterator().next();
            }
        } else {
            // Check if the file extension matches
            String ext = FilenameUtils.getExtension(path.getFileName().toString());
            if (ext != null && ext.length() > 0) {
                return fileExtensionLookup.get(ext.toLowerCase());
            }
        }
        return null;
    }

    /**
     * Returns if the given file has a supported content type
     * @param path the file to check
     * @return if the given file has a supported content type
     */
    public boolean isSupportedContentType(Path path) {
        return (getSupportedContentType(path) != null);
    }

    /**
     * Returns the icon to display for the given file
     * @param path the file to check
     * @param iconSize the size of the icon
     * @return the icon to display for the given file
     */
    public String getIcon(Path path, IconSize iconSize) {
        ContentType type = getSupportedContentType(path);
        if (type == null) {
            type = genericType;
        }
        return type.getIconPath(iconSize);
    }

    /**
     * Helper class that encapsulated a content type
     */
    class ContentType {
        String mimeType;
        String[] fileExtensions;
        String icon;

        public ContentType(String mimeType, String icon, String... fileExtensions) {
            this.mimeType = mimeType;
            this.fileExtensions = fileExtensions;
            this.icon = icon;
        }

        public String getMimeType() { return mimeType; }

        public String[] getFileExtensions() { return fileExtensions; }

        public String getIcon() { return icon; }

        public String getIconPath(IconSize iconSize) {
            if (icon == null) {
                return String.format("/%s/%s-%d_32.png", FILE_TYPE_FOLDER, GENERIC_ICON, iconSize.getSize());
            } else {
                return String.format("/%s/%s/%s-%d_32.png", FILE_TYPE_FOLDER, getIcon(), getIcon(), iconSize.getSize());
            }
        }
    }
}
