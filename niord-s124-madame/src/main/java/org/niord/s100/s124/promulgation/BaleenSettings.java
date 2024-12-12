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

package org.niord.s100.s124.promulgation;

import org.apache.commons.lang.StringUtils;
import org.niord.core.model.BaseEntity;
import org.niord.core.promulgation.PromulgationType;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.validation.constraints.NotNull;

/**
 * Stores the settings for a Twitter promulgation type.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="BaleenSettings.findByType",
                query="SELECT t FROM BaleenSettings t where t.promulgationType.typeId = :typeId")
})
public class BaleenSettings extends BaseEntity<Integer> {

    @NotNull
    @ManyToOne
    PromulgationType promulgationType;

    String accessToken;
    String accessTokenSecret;


    /** Constructor **/
    public BaleenSettings() {
    }


    /** Constructor **/
    public BaleenSettings(BaleenSettingsVo settings) {
        this.promulgationType = new PromulgationType(settings.getPromulgationType());
        this.accessToken = settings.getAccessToken();
        this.accessTokenSecret = settings.getAccessTokenSecret();
    }


    /** Updates the settings **/
    public void updateSettings(BaleenSettings settings) {
        this.accessToken = settings.getAccessToken();
        this.accessTokenSecret = settings.getAccessTokenSecret();
    }


    /** Returns a value object representation of this entity **/
    public BaleenSettingsVo toVo() {
        BaleenSettingsVo settings = new BaleenSettingsVo();
        settings.setPromulgationType(promulgationType.toVo());
        settings.setAccessToken(accessToken);

        // NB: We never send passwords back to the client
        //settings.setApiSecret(apiSecret);
        settings.setAccessTokenSecret(accessTokenSecret);

        return settings;
    }


    /** Returns if the credentials are valid **/
    public boolean credentialsValid() {
        return
                StringUtils.isNotBlank(accessToken)
                && StringUtils.isNotBlank(accessTokenSecret);
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public PromulgationType getPromulgationType() {
        return promulgationType;
    }

    public void setPromulgationType(PromulgationType promulgationType) {
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
