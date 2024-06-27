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

package org.niord.s124madame.promulgation;

import org.niord.core.promulgation.vo.PromulgationTypeVo;
import org.niord.model.IJsonSerializable;

/**
 * Value object for the TwitterSettings entity
 */
@SuppressWarnings("unused")
public class BaleenSettingsVo implements IJsonSerializable {

    PromulgationTypeVo promulgationType;
    String accessToken;
    String accessTokenSecret;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public PromulgationTypeVo getPromulgationType() {
        return promulgationType;
    }

    public void setPromulgationType(PromulgationTypeVo promulgationType) {
        this.promulgationType = promulgationType;
    }


    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
    }
}
