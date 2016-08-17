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
package org.niord.core.cache;

import java.io.Serializable;

/**
 * Can be used in JCache/Infinispan to wrap the stored element.
 * This way, you can cache a value of "null"
 */
@SuppressWarnings("unused")
public class CacheElement<T> implements Serializable {

    private static final long serialVersionUID = 1L;
    T element;

    /**
     * Constructor
     * @param element the element being wrapped
     */
    public CacheElement(T element) {
        this.element = element;
    }

    public T getElement() {
        return element;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return (element == null) ? 0 : element.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheElement that = (CacheElement) o;

        return !(element != null ? !element.equals(that.element) : that.element != null);

    }

    /**
     * Returns if the wrapped element is null or not
     * @return if the wrapped element is null or not
     */
    public boolean isNull() {
        return (element == null);
    }
}
