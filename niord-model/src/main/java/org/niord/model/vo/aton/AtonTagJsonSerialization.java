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
package org.niord.model.vo.aton;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Used for serializing and de-serializing an AtonTagVo array as Maps.
 */
public class AtonTagJsonSerialization {

    /**
     * Serializes an AtonTagVo array as a Map
     */
    public static class Serializer extends JsonSerializer<AtonTagVo[]> {

        /** {@inheritDoc} */
        @Override
        public void serialize(AtonTagVo[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

            // NB: We collect to a linked hash map to preserve the order of the tags
            Map<String, String> tags = Arrays.stream(value)
                    .collect(Collectors.toMap(AtonTagVo::getK, AtonTagVo::getV, (t1,t2) -> t1, LinkedHashMap::new));
            gen.writeObject(tags);
        }
    }

    /**
     * De-serializes a Map as an AtonTagVo array
     */
    public static class Deserializer extends JsonDeserializer<AtonTagVo[]> {

        /** {@inheritDoc} */
        @Override
        public AtonTagVo[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            List<AtonTagVo> tags = new ArrayList<>();

            if (node != null) {
                for (Iterator<String> i = node.fieldNames(); i.hasNext(); ) {
                    String k = i.next();
                    String v = node.get(k).asText();
                    tags.add(new AtonTagVo(k, v));
                }
            }
            return  tags.toArray(new AtonTagVo[tags.size()]);
        }
    }

}
