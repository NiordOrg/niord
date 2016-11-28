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

package org.niord.core.util;

import org.apache.commons.lang.StringUtils;

import java.util.UUID;

/**
 * Utility methods for creating and using UIDs (or strictly speaking, UUIDs)
 */
public class UidUtils {

    private UidUtils() {
    }


    /**
     * Returns a new UID
     * @return a new UID
     */
    public static String newUid() {
        return UUID.randomUUID().toString();
    }


    /**
     * Validates that the uid has the proper UUID format
     * @param uid the UID to validate
     * @return if the UID has a valid UUID format
     */
    @SuppressWarnings("all")
    public static boolean validUidFormat(String uid) {
        try{
            UUID uuid = UUID.fromString(uid);
            return true;
        } catch (IllegalArgumentException exception){
            return false;
        }
    }


    /**
     * Converts the uid to a hashed path withing the specified root path
     * <p>
     * To allow for a more efficient file directory structure,
     * the folders defined by the uid are nested into two layers
     * of sub-directories. The hashing used for the directories
     * is based on the first 3 (1+2) characters of the UUID associated
     * with the messages, yielding approx 4K folders.
     *
     * @param rootPath the root path
     * @param uid the UID
     * @return the associated hashed path
     */
    public static String uidToHashedFolderPath(String rootPath, String uid) {
        if (StringUtils.isBlank(uid)) {
            return null;
        }
        if (!validUidFormat(uid)) {
            throw new IllegalArgumentException("Invalid UID format " + uid);
        }

        // Compose the path as "messages/uid[0;0]/uid[1;2]/uid"
        return String.format(
                "%s/%s/%s/%s",
                rootPath,
                uid.substring(0,1),
                uid.substring(1,3),
                uid);
    }

}
