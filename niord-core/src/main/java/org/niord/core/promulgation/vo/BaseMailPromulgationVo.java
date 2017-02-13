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

package org.niord.core.promulgation.vo;

import org.apache.commons.lang.StringUtils;
import org.niord.core.promulgation.BasePromulgation;

/**
 * Abstract super-class for mailing list-based promulgation data value objects.
 */
public abstract class BaseMailPromulgationVo<P extends BasePromulgation> extends BasePromulgationVo<P> {

    String text;


    /** {@inheritDoc} **/
    @Override
    public boolean promulgationDataDefined() {
        return StringUtils.isNotBlank(text);
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
