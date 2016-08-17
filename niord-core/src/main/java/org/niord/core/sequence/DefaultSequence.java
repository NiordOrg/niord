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
package org.niord.core.sequence;

/**
 * A default sequence implementation
 */
public final class DefaultSequence implements Sequence {

    String name;
    long initialValue;

    @SuppressWarnings("unused")
    public DefaultSequence(String name) {
        this(name, 0);
    }

    public DefaultSequence(String name, long initialValue) {
        this.name = name;
        this.initialValue = initialValue;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long initialValue() {
        return initialValue;
    }
}
