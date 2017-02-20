/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.promulgation.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.promulgation.PromulgationType;
import org.niord.core.promulgation.vo.PromulgationTypeVo;

import javax.inject.Named;

/**
 * Converts the promulgation type value object into a promulgation type entity template
 */
@Named
public class BatchPromulgationTypeImportProcessor extends AbstractItemHandler {

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        PromulgationTypeVo typeVo = (PromulgationTypeVo) item;

        return new PromulgationType(typeVo);
    }
}
