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
package org.niord.core.aton.vo;

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
