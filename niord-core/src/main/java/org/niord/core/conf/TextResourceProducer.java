/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
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
