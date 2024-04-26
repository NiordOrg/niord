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
package org.niord.core.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.AttributeConverter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Use to convert a JPA database field to and fro a Map<String, Object> object by (de-)serializing as JSON.
 * Usage:
 * <pre>
 *     {@literal @}Convert(converter = JpaPropertiesAttributeConverter.class)
 * </pre>
 */
public class JpaPropertiesAttributeConverter implements AttributeConverter<Map<String, Object>, String> {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final static Logger log = LoggerFactory.getLogger(JpaPropertiesAttributeConverter.class);

    /** {@inheritDoc} */
    @Override
    public String convertToDatabaseColumn(Map<String, Object> value) {

        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.error("Error converting Properties to String: " + value);
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> convertToEntityAttribute(String value) {

        if (value == null) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>(){});
        } catch (IOException ex) {
            log.error("Error converting String to Properties: " + value);
            return new HashMap<>();
        }
    }
}
