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
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.TwitterMessagePromulgationVo;
import org.niord.model.DataFilter;
import org.niord.model.message.MessageDescVo;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;

/**
 * Manages Twitter message promulgations
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class TwitterPromulgationService extends BasePromulgationService {

    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/

    /** {@inheritDoc} */
    @Override
    public String getServiceId() {
        return TwitterMessagePromulgation.TYPE;
    }


    /** {@inheritDoc} */
    @Override
    public String getServiceName() {
        return "Twitter";
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public void onLoadSystemMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        TwitterMessagePromulgationVo twitter = message.promulgation(TwitterMessagePromulgationVo.class, type.getTypeId());
        if (twitter == null) {
            twitter = new TwitterMessagePromulgationVo(type.toVo(DataFilter.get()));
            message.checkCreatePromulgations().add(twitter);
        }
    }


    /** {@inheritDoc} */
    @Override
    public BaseMessagePromulgationVo generateMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {

        TwitterMessagePromulgationVo twitter = new TwitterMessagePromulgationVo();

        MessageDescVo desc = message.getDesc(getLanguage(type));
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
