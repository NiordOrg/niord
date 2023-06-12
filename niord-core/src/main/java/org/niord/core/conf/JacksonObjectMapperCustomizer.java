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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.quarkus.jackson.ObjectMapperCustomizer;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;

import javax.inject.Singleton;

/**
 * Customize the Jackson Mapping Operations.
 *
 * This is required for the promulgation VO polymorphism.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
@Singleton
public class JacksonObjectMapperCustomizer implements ObjectMapperCustomizer {

    public void customize(ObjectMapper mapper) {
        mapper.setPolymorphicTypeValidator(BasicPolymorphicTypeValidator
                .builder()
                .allowIfBaseType(BaseMessagePromulgationVo.class)
                .build());
    }

}
