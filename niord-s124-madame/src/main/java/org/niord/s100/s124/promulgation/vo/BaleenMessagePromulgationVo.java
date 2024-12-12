/*
 * Copyright (c) 2008 Kasper Nielsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.niord.s100.s124.promulgation.vo;

import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.PromulgationTypeVo;
import org.niord.s100.s124.promulgation.BaleenMessagePromulgation;

/**
 *
 */
public class BaleenMessagePromulgationVo extends BaseMessagePromulgationVo<BaleenMessagePromulgation>{

    /** Constructor **/
    public BaleenMessagePromulgationVo() {
    }


    /** Constructor **/
    public BaleenMessagePromulgationVo(PromulgationTypeVo type) {
        super(type);
    }

    /** {@inheritDoc} */
    @Override
    public BaleenMessagePromulgation toEntity() {
        return new BaleenMessagePromulgation(this);
    }

    /** {@inheritDoc} */
    @Override
    public boolean promulgationDataDefined() {
        return true;
    }

}
