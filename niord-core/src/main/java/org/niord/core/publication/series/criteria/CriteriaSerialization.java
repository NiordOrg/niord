package org.niord.core.publication.series.criteria;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * The canonical serialization.
 *
 * Named in one place because "byte-stable" has no meaning otherwise. The same
 * instance writes the column and the assertion, so a document that round-trips
 * cannot round-trip differently in a test than it does in the database.
 *
 * Each setting earns its place:
 *  - properties sorted alphabetically and map entries by key, so field order is
 *    deterministic and independent of Java declaration order
 *  - NON_NULL, so an absent optional never comes back as an explicit null
 *  - indentation off, because the bytes are a column value, not a document
 *  - dates as timestamps, matching every other date on this wire
 */
public final class CriteriaSerialization {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private CriteriaSerialization() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
