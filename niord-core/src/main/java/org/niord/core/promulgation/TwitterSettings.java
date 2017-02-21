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

package org.niord.core.promulgation;

import org.apache.commons.lang.StringUtils;
import org.niord.core.model.BaseEntity;
import org.niord.core.promulgation.vo.TwitterSettingsVo;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;

/**
 * Stores the settings for a Twitter promulgation type.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="TwitterSettings.findByType",
                query="SELECT t FROM TwitterSettings t where t.promulgationType.typeId = :typeId")
})
@SuppressWarnings("unused")
public class TwitterSettings extends BaseEntity<Integer> {

    @NotNull
    @ManyToOne
    PromulgationType promulgationType;

    String format;
    Boolean includeThumbnail;

    String apiKey;
    String apiSecret;
    String accessToken;
    String accessTokenSecret;


    /** Constructor **/
    public TwitterSettings() {
    }


    /** Constructor **/
    public TwitterSettings(TwitterSettingsVo settings) {
        this.promulgationType = new PromulgationType(settings.getPromulgationType());
        this.format = settings.getFormat();
        this.includeThumbnail = settings.getIncludeThumbnail();
        this.apiKey = settings.getApiKey();
        this.apiSecret = settings.getApiSecret();
        this.accessToken = settings.getAccessToken();
        this.accessTokenSecret = settings.getAccessTokenSecret();
    }


    /** Updates the settings **/
    public void updateSettings(TwitterSettings settings) {
        this.format = settings.getFormat();
        this.includeThumbnail = settings.getIncludeThumbnail();
        this.apiKey = settings.getApiKey();
        this.apiSecret = settings.getApiSecret();
        this.accessToken = settings.getAccessToken();
        this.accessTokenSecret = settings.getAccessTokenSecret();
    }


    /** Returns a value object representation of this entity **/
    public TwitterSettingsVo toVo() {
        TwitterSettingsVo settings = new TwitterSettingsVo();
        settings.setPromulgationType(promulgationType.toVo());
        settings.setFormat(format);
        settings.setIncludeThumbnail(includeThumbnail);
        settings.setApiKey(apiKey);
        settings.setAccessToken(accessToken);

        // NB: We never send passwords back to the client
        //settings.setApiSecret(apiSecret);
        // settings.setAccessTokenSecret(accessTokenSecret);

        return settings;
    }


    /** Returns if the credentials are valid **/
    public boolean credentialsValid() {
        return StringUtils.isNotBlank(apiKey)
                && StringUtils.isNotBlank(apiSecret)
                && StringUtils.isNotBlank(accessToken)
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

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Boolean getIncludeThumbnail() {
        return includeThumbnail;
    }

    public void setIncludeThumbnail(Boolean includeThumbnail) {
        this.includeThumbnail = includeThumbnail;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
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
