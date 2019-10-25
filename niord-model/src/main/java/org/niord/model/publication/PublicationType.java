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

package org.niord.model.publication;

/**
 * Defines the type of publication resource that the publications links to.
 */
public enum PublicationType {

    /**
     * No link or file is specified directly
     */
    NONE,

    /**
     * The publication resource is defined via a link
     */
    LINK,

    /**
     * The publication resource is uploaded to the Niord repository
     */
    REPOSITORY,

    /**
     * The publication resource is defined via a message report and possibly uploaded to the Niord repository
     */
    MESSAGE_REPORT

}
