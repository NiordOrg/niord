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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The type of the message series identifier
 */
public enum MainType {
    NW,
    NM;

    /** Returns all the sub-type implied by this main type */
    public Set<Type> getTypes() {
        return Arrays.stream(Type.values())
                .filter(t -> t.getMainType() == this)
                .collect(Collectors.toSet());
    }

    /** Returns a sub-type implied by this main type */
    public Type anyType() {
        return getTypes().iterator().next();
    }
}
