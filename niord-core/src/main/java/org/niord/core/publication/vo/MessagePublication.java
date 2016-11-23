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
 * Defines how a publication can be referenced from a message.
 */
public enum MessagePublication {

    /** The publication cannot be referenced from a message **/
    NONE,

    /** The publication can be referenced from the message "internal publication" field, i.e. only used internally **/
    INTERNAL,

    /** The publication can be referenced from the message "publication" field, i.e. displayed externally **/
    EXTERNAL

}
