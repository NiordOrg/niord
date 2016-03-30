package org.niord.core.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.AttributeConverter;
import java.io.IOException;

/**
 * Use to convert a JPA database field to and fro JSON.
 * Usage:
 * <pre>
 *     {@literal @}Convert(converter = JpaConverterJson.class)
 * </pre>
 */
public class JpaJsonAttributeConverter implements AttributeConverter<Object, String> {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final static Logger log = LoggerFactory.getLogger(JpaJsonAttributeConverter.class);

    /** {@inheritDoc} */
    @Override
    public String convertToDatabaseColumn(Object value) {

        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.error("Error converting JSON to String: " + value);
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Object convertToEntityAttribute(String value) {

        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value, Object.class);
        } catch (IOException ex) {
            log.error("Error converting String to JSON: " + value);
            return null;
        }
    }
}
