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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The type of reference
 */
public enum ReferenceType {
    /**
     * Reference reference type.
     */
    REFERENCE,
    /**
     * Repetition reference type.
     */
    REPETITION,
    /**
     * Repetition new time reference type.
     */
    REPETITION_NEW_TIME,
    /**
     * Cancellation reference type.
     */
    CANCELLATION,
    /**
     * Update reference type.
     */
    UPDATE;

    /**
     * Returns all statuses that will cause the referenced message to be cancelled  @return the set
     */
    public static Set<ReferenceType> cancelsReferencedMessage() {
        return new HashSet<>(Arrays.asList(REPETITION, REPETITION_NEW_TIME, CANCELLATION, UPDATE));
    }
}
