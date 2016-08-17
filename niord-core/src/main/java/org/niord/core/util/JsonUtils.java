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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JSON-related utility methods
 */
@SuppressWarnings("unused")
public class JsonUtils {

    /**
     * Parses the json data as an entity of the given class
     *
     * @param data the json data to parse
     * @param dataClass the class of the data
     * @return the parsed data
     */
    public static <T> T fromJson(String data, Class<T> dataClass) throws IOException {

        if (data == null) {
            return null;
        }

        ObjectMapper jsonMapper = new ObjectMapper();
        return jsonMapper.readValue(data, dataClass);
    }


    /**
     * Parses the json data as an entity of the given class
     *
     * @param data the json data to parse
     * @param typeRef the type reference
     * @return the parsed data
     */
    public static <T> T fromJson(String data, TypeReference typeRef) throws IOException {

        if (data == null) {
            return null;
        }

        ObjectMapper jsonMapper = new ObjectMapper();
        return jsonMapper.readValue(data, typeRef);
    }


    /**
     * Formats the entity as json data
     *
     * @param data the entity to format
     * @return the json data
     */
    public static String toJson(Object data) throws IOException {

        if (data == null) {
            return null;
        }

        ObjectMapper jsonMapper = new ObjectMapper();
        return jsonMapper.writeValueAsString(data);
    }


    /**
     * Reads the data from a JSON file at the given path
     *
     * @param path the path to read the JSON data from
     * @return the parsed JSON data
     */
    public static <T> T readJson(Class<T> dataClass, Path path) throws IOException {

        if (path == null) {
            return null;
        }

        ObjectMapper jsonMapper = new ObjectMapper();
        return jsonMapper.readValue(path.toFile(), dataClass);
    }


    /**
     * Reads the data from a JSON file at the given path
     *
     * @param typeRef the type reference
     * @param path the path to read the JSON data from
     * @return the parsed JSON data
     */
    public static <T> T readJson(TypeReference typeRef, Path path) throws IOException {

        if (path == null) {
            return null;
        }

        ObjectMapper jsonMapper = new ObjectMapper();
        return jsonMapper.readValue(path.toFile(), typeRef);
    }


    /**
     * Writes the data as a JSON file at the given path
     *
     * @param data the entity to write
     * @param path the path to write the data to
     */
    public static void writeJson(Object data, Path path) throws IOException {

        if (data == null || path == null) {
            return;
        }

        ObjectMapper jsonMapper = new ObjectMapper();
        jsonMapper.writeValue(path.toFile(), data);
    }

}
