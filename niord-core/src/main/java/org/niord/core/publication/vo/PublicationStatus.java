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

package org.niord.core.publication.vo;

/**
 * The status of a publication
 */
public enum PublicationStatus {

    /**
     * Draft is the initial state of a publication before it is made active
     */
    DRAFT,

    /**
     * Special draft status for message report-based publications. In this status
     * published message will be added to the publication message tag.
     */
    RECORDING,

    /**
     * The state of an active publication.
     * Only active publications will be published (if the associated category has the publish flag set).
     */
    ACTIVE,

    /**
     * The state of an inactive publication.
     */
    INACTIVE;

    /** Returns if the status is a draft status **/
    public boolean isDraft() {
        return this == DRAFT || this == RECORDING;
    }

}
