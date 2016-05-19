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
package org.niord.core.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.AttributeConverter;
import java.io.IOException;
import java.util.Properties;

/**
 * Use to convert a JPA database field to and fro a Properties object by (de-)serializing as JSON.
 * Usage:
 * <pre>
 *     {@literal @}Convert(converter = JpaMapAttributeConverter.class)
 * </pre>
 */
public class JpaPropertiesAttributeConverter implements AttributeConverter<Properties, String> {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final static Logger log = LoggerFactory.getLogger(JpaPropertiesAttributeConverter.class);

    /** {@inheritDoc} */
    @Override
    public String convertToDatabaseColumn(Properties value) {

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
    public Properties convertToEntityAttribute(String value) {

        if (value == null) {
            return new Properties();
        }

        try {
            return objectMapper.readValue(value, Properties.class);
        } catch (IOException ex) {
            log.error("Error converting String to Properties: " + value);
            return new Properties();
        }
    }
}
