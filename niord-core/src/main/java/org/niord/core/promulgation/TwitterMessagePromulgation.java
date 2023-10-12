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

import org.niord.core.promulgation.vo.TwitterMessagePromulgationVo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Defines the message promulgation entity associated with Twitter promulgation
 */
@Entity
@DiscriminatorValue(TwitterMessagePromulgation.SERVICE_ID)
@SuppressWarnings("unused")
public class TwitterMessagePromulgation extends BaseMessagePromulgation<TwitterMessagePromulgationVo> {

    public static final String SERVICE_ID = "twitter";

    // NB: Tweets can be longer than 140 chars nowadays, if they e.g. contain multimedia links...
    @Column(length = 512)
    String tweet;


    /** Constructor **/
    public TwitterMessagePromulgation() {
        super();
    }


    /** Constructor **/
    public TwitterMessagePromulgation(TwitterMessagePromulgationVo promulgation) {
        super(promulgation);
        this.tweet = promulgation.getTweet();
    }


    /** Returns a value object for this entity */
    @Override
    public TwitterMessagePromulgationVo toVo() {
        TwitterMessagePromulgationVo data = toVo(new TwitterMessagePromulgationVo());
        data.setTweet(tweet);
        return data;
    }

    /** Updates this promulgation from another promulgation **/
    @Override
    public void update(BaseMessagePromulgation promulgation) {
        if (promulgation instanceof TwitterMessagePromulgation) {
            super.update(promulgation);
            TwitterMessagePromulgation p = (TwitterMessagePromulgation)promulgation;
            this.tweet = p.getTweet();
        }
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    public String getTweet() {
        return tweet;
    }

    public void setTweet(String tweet) {
        this.tweet = tweet;
    }
}
