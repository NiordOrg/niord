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

import org.niord.core.message.Message;

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


    /** {@inheritDoc} */
    @Override
    public void onNewTemplateMessage(Message message) {
        message.getPromulgations().add(new TwitterPromulgation());
    }


    /** {@inheritDoc} */
    @Override
    public void onCreateMessage(Message message) {
        message.getPromulgations().forEach(p -> System.out.println("SAVING " + p.getType()));
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessage(Message message) {
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessageStatus(Message message) {
    }
}
