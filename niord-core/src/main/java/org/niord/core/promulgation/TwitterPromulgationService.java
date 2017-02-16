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
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.BasePromulgationVo;
import org.niord.core.promulgation.vo.TwitterPromulgationVo;
import org.niord.model.message.MessageDescVo;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Manages Twitter promulgations
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class TwitterPromulgationService extends BasePromulgationService {

    public static final int PRIORITY = 100;


    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/

    /**
     * Registers the promulgation service with the promulgation manager
     */
    @PostConstruct
    public void init() {
        registerPromulgationService();
    }


    /** {@inheritDoc} */
    @Override
    public String getType() {
        return TwitterPromulgation.TYPE;
    }


    /** {@inheritDoc} */
    @Override
    public int getDefaultPriority() {
        return PRIORITY;
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public void onLoadSystemMessage(SystemMessageVo message) throws PromulgationException {
        TwitterPromulgationVo twitter = message.promulgation(TwitterPromulgationVo.class, getType());
        if (twitter == null) {
            twitter = new TwitterPromulgationVo();
            message.getPromulgations().add(twitter);
        }
    }


    /** {@inheritDoc} */
    @Override
    public BasePromulgationVo generateMessagePromulgation(SystemMessageVo message) throws PromulgationException {
        TwitterPromulgationVo twitter = new TwitterPromulgationVo();

        MessageDescVo desc = message.getDesc("en");
        String title = desc != null ? desc.getTitle() : null;

        if (StringUtils.isNotBlank(title)) {
            twitter.setPromulgate(true);
            twitter.setTweet(title);
        } else {
            twitter.setPromulgate(false);
        }

        return twitter;
    }

}
