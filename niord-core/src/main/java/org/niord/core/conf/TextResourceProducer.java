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
package org.niord.core.conf;

import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Produces the {@code @TextResource} injections.
 */
@SuppressWarnings("unused")
public class TextResourceProducer {

    private static Map<String, String> TEXT_CACHE = new ConcurrentHashMap<>();

    @Produces
    @TextResource
    public String loadTextFromResource(InjectionPoint ip) {
        String resourceName = ip.getAnnotated().getAnnotation(TextResource.class).value();
        Class<?> clazz = ip.getMember().getDeclaringClass();
        return loadResourceText(clazz, resourceName);
    }

    /**
     * Loads, caches and returns the text of the given file. The file must be placed
     * in the same package as the class.
     *
     * @param clazz the class which defines the location of the file
     * @param resourceName the name of the file
     * @return the text content of the file
     */
    public static String loadResourceText(Class<?> clazz, String resourceName) {
        // Check if the resource is cached
        String cacheKey = clazz.getName() + "-" + resourceName;
        if (TEXT_CACHE.containsKey(cacheKey)) {
            return TEXT_CACHE.get(cacheKey);
        }

        // Not cached - load it
        try (BufferedReader r = new BufferedReader(new InputStreamReader(clazz.getResourceAsStream(resourceName), "UTF-8"))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                result.append(line).append(System.lineSeparator());
            }
            TEXT_CACHE.put(cacheKey, result.toString());
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Undefined resource " + resourceName + " relative to class " + clazz, ex);
        }
    }
}
