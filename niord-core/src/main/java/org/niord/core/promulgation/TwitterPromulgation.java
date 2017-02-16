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

import org.niord.core.promulgation.vo.TwitterPromulgationVo;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Defines the promulgation data associated with Twitter promulgation
 */
@Entity
@DiscriminatorValue(TwitterPromulgation.TYPE)
@SuppressWarnings("unused")
public class TwitterPromulgation extends BasePromulgation<TwitterPromulgationVo> {

    public static final String  TYPE = "twitter";

    // NB: Tweets can be longer than 140 chars nowadays, if they e.g. contain multimedia links...
    @Column(length = 512)
    String tweet;


    /** Constructor **/
    public TwitterPromulgation() {
        super();
        this.type = TYPE;
    }


    /** Constructor **/
    public TwitterPromulgation(TwitterPromulgationVo promulgation) {
        super(promulgation);
        this.type = TYPE;
        this.tweet = promulgation.getTweet();
    }


    /** Returns a value object for this entity */
    public TwitterPromulgationVo toVo() {
        TwitterPromulgationVo data = toVo(new TwitterPromulgationVo());
        data.setTweet(tweet);
        return data;
    }

    /** Updates this promulgation from another promulgation **/
    @Override
    public void update(BasePromulgation promulgation) {
        if (promulgation instanceof TwitterPromulgation) {
            super.update(promulgation);
            TwitterPromulgation p = (TwitterPromulgation)promulgation;
            this.tweet = p.getTweet();
        }
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getTweet() {
        return tweet;
    }

    public void setTweet(String tweet) {
        this.tweet = tweet;
    }
}
