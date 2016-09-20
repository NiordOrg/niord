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
package org.niord.model.message;

/**
 * Defines the message status
 */
public enum Status {

    /**
     * Draft is the initial state of a message before it has been published
     */
    DRAFT,

    /**
     * Verified is the state of a message before it has been published
     */
    VERIFIED,

    /**
     * The state of a message that has been published, and has not yet expired
     */
    PUBLISHED,

    /**
     * The state of a message that has been published and subsequently has expired,
     * i.e. the validTo date has been passed
     */
    EXPIRED,

    /**
     * The state of a message that has been published and subsequently
     * has been manually cancelled
     */
    CANCELLED,

    /**
     * The state of a draft that has been deleted
     */
    DELETED;


    /** Returns the message status that unauthorized users can view **/
    public boolean isPublic() {
        return this == PUBLISHED || this == CANCELLED || this == EXPIRED;
    }
}
