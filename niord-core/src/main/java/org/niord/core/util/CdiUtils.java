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

import javax.enterprise.inject.spi.CDI;
import javax.naming.NamingException;

/**
 * Can be used to force injection in classes that do not support CDI
 */
public class CdiUtils {

    /**
     * Don't instantiate this class
     */
    private CdiUtils() {
    }

    /**
     * Looks up a CDI managed bean with the given class
     *
     * @param clazz The class of the object to look up
     * @return the object with the given class
     */
    @SuppressWarnings({ "unchecked", "unused" })
    public static <T> T getBean(Class<T> clazz) throws NamingException {
        return CDI.current().select(clazz).get();
    }
}
