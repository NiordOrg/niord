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

package org.niord.core.template;

import org.niord.core.model.DescEntity;
import org.niord.core.template.vo.ListParamValueDescVo;
import org.niord.model.ILocalizedDesc;

import javax.persistence.Entity;

/**
 * Localized contents for the {@code ListParamValue} entity
 */
@Entity
@SuppressWarnings("unused")
public class ListParamValueDesc extends DescEntity<ListParamValue> {

    private String shortValue;
    private String longValue;


    /** Constructor **/
    public ListParamValueDesc() {
    }


    /** Constructor **/
    public ListParamValueDesc(ListParamValueDescVo desc) {
        super(desc);
        copyDesc(desc);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void copyDesc(ILocalizedDesc desc) {
        if (!(desc instanceof ListParamValueDesc)) {
            throw new IllegalArgumentException("Invalid desc class " + desc);
        }
        this.shortValue = ((ListParamValueDesc)desc).getShortValue();
        this.longValue = ((ListParamValueDesc)desc).getLongValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean descDefined() {
        return ILocalizedDesc.fieldsDefined(shortValue, longValue);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

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

