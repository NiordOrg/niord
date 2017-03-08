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

package org.niord.core.category.vo;


import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizedDesc;

/**
 * The entity description VO
 */
@SuppressWarnings("unused")
public class ListParamValueDescVo implements ILocalizedDesc, IJsonSerializable {

    String lang;
    String shortValue;
    String longValue;

    /** {@inheritDoc} */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(shortValue, longValue);
    }


    /** {@inheritDoc} */
    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        ListParamValueDescVo desc = (ListParamValueDescVo)localizedDesc;
        this.shortValue = desc.getShortValue();
        this.longValue = desc.getLongValue();
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
    public String getLang() {
        return lang;
    }

    @Override
    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getShortValue() {
        return shortValue;
    }

    public void setShortValue(String shortValue) {
        this.shortValue = shortValue;
    }

    public String getLongValue() {
        return longValue;
    }

    public void setLongValue(String longValue) {
        this.longValue = longValue;
    }
}
