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
import org.niord.core.NiordApp;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.Message;
import org.niord.core.message.MessageService;
import org.niord.core.message.MessageTokenExpander;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.TwitterMessagePromulgationVo;
import org.niord.model.DataFilter;
import org.niord.model.message.MessageDescVo;
import org.niord.model.message.Status;
import twitter4j.GeoLocation;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

import javax.ejb.Asynchronous;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

/**
 * Manages Twitter message promulgations
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class TwitterPromulgationService extends BasePromulgationService {

    public static final int MAX_TWEET_LENGTH = 140;
    public static final String DEFAULT_TWEET_FORMAT = "${short-id} ${tweet} ${base-uri}/#/message/${uid}";

    // TODO: Consider using AsyncTwitterFactory instead
    TwitterFactory twitterFactory = new TwitterFactory();

    @Inject
    PromulgationTypeService promulgationTypeService;

    @Inject
    MessageService messageService;

    @Inject
    NiordApp app;


    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public String getServiceId() {
        return TwitterMessagePromulgation.SERVICE_ID;
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


    /***************************************/
    /** Generating promulgations          **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public BaseMessagePromulgationVo generateMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {

        TwitterMessagePromulgationVo twitter = new TwitterMessagePromulgationVo(type.toVo(DataFilter.get()));

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


    /** {@inheritDoc} */
    @Override
    public void resetMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        TwitterMessagePromulgationVo twitter = message.promulgation(TwitterMessagePromulgationVo.class, type.getTypeId());
        if (twitter != null) {
            twitter.reset();
        }
    }

    /***************************************/
    /** Twitter settings                  **/
    /***************************************/

    /**
     * Returns the Twitter settings for the given type or null if not found
     * @param typeId the promulgation type
     * @return the Twitter settings for the given type or null if not found
     */
    public TwitterSettings getSettings(String typeId) {
        try {
            return em.createNamedQuery("TwitterSettings.findByType", TwitterSettings.class)
                    .setParameter("typeId", typeId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /** Creates a Twitter settings entity from the given template **/
    public TwitterSettings createSettings(TwitterSettings settings) throws Exception {

        String typeId = settings.getPromulgationType().getTypeId();

        TwitterSettings original = getSettings(typeId);
        if (original != null) {
            throw new IllegalArgumentException("Settings already exists for promulgation type " + typeId);
        }

        settings.setPromulgationType(promulgationTypeService.getPromulgationType(typeId));

        log.info("Create Twitter Settings for promulgation type " + typeId);
        return  saveEntity(settings);
    }


    /** Updates a Twitter settings entity from the given template **/
    public TwitterSettings updateSettings(TwitterSettings settings) throws Exception {

        String typeId = settings.getPromulgationType().getTypeId();

        TwitterSettings original = getSettings(typeId);
        if (original == null) {
            throw new IllegalArgumentException("No Settings exists for promulgation type " + typeId);
        }

        original.updateSettings(settings);

        log.info("Updating Twitter Settings for promulgation type " + typeId);
        return  saveEntity(original);
    }


    /***************************************/
    /** Twitter promulgation              **/
    /***************************************/


    /**
     * Handle Twitter promulgation for the message
     * @param messageUid the UID of the message
     */
    @Asynchronous
    public void checkPromulgateMessage(String messageUid) {

        Message message = messageService.findByUid(messageUid);

        if (message != null && message.getStatus() == Status.PUBLISHED) {
            message.getPromulgations().stream()
                    .filter(p -> p.isPromulgate() && p.promulgationDataDefined() && p.getType().isActive())
                    .filter(p -> p instanceof TwitterMessagePromulgation)
                    .map(p -> (TwitterMessagePromulgation) p)
                    .forEach(t -> promulgateMessage(message, t));
        }

    }


    /**
     * Handle Twitter promulgation for the message
     * @param message the message
     * @param messagePromulgation the Twitter message promulgation
     */
    private void promulgateMessage(Message message, TwitterMessagePromulgation messagePromulgation) {

        long t0 = System.currentTimeMillis();

        PromulgationType type = messagePromulgation.getType();
        TwitterSettings settings = getSettings(type.getTypeId());

        // Check that the settings are valid
        if (settings == null || !settings.credentialsValid()) {
            return;
        }

        // Instantiate the Twitter client
        Twitter twitter = instantiateTwitter(settings);
        if (twitter == null) {
            return;
        }

        // Instantiate and initialize a new twitter status update
        String tweet = computeTweet(message, settings.getFormat(), messagePromulgation.getTweet());
        StatusUpdate statusUpdate = new StatusUpdate(tweet);

        // Compute the location
        GeoLocation location = computeLocation(message);
        if (location != null) {
            statusUpdate.setLocation(location);

            // Attach image if enabled in the settings
            if (settings.getIncludeThumbnail() != null && settings.getIncludeThumbnail()) {
                String imageUrl = String.format("%s/rest/message-map-image/%s.png", app.getBaseUri(), message.getUid());
                try {
                    // NB: Input stream seems to be closed by Twitter
                    InputStream in = new URL(imageUrl).openStream();
                    statusUpdate.setMedia(
                            StringUtils.defaultIfBlank(message.getShortId(), ""),
                            in);
                } catch (Exception ex) {
                    // Report error, but proceed without image
                    log.warn("Failed adding image " + imageUrl + " to tweet for message " + message.getUid());
                }
            }
        }

        try {
            //tweet or update status
            twitter.updateStatus(statusUpdate);
            log.info("Promulgated to Twitter for message " + message.getUid() +
                     " in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (TwitterException e) {
            log.warn("Failed promulgating to Twitter for message " + message.getUid(), e);
        }
    }


    /** Composes the tweet **/
    private String computeTweet(Message message, String format, String tweet) {
        format = StringUtils.defaultIfBlank(format, DEFAULT_TWEET_FORMAT);

        MessageTokenExpander expander = MessageTokenExpander.getInstance(
                message,
                Arrays.asList(app.getLanguages()),
                app.getDefaultLanguage());
        expander.token("${base-uri}", app.getBaseUri());

        // First, update the tweet
        tweet = expander.expandTokens(tweet);
        tweet = StringUtils.abbreviate(tweet, MAX_TWEET_LENGTH);

        // Then update the tweet template
        expander.token("${tweet}", tweet);
        return expander.expandTokens(format);
    }


    /**
     * Compute the approximate center location of the message
     * @param message the message
     * @return the approximate center location of the message
     */
    private GeoLocation computeLocation(Message message) {
        // Compute the message center
        double[] center = GeoJsonUtils.computeCenter(message.toGeoJson());
        if (center != null) {
            return new GeoLocation(center[1], center[0]);
        }
        return null;
    }


    /** Instantiates a Twitter client **/
    private Twitter instantiateTwitter(TwitterSettings settings) {
        try {

            //Instantiate a new Twitter instance
            Twitter twitter = twitterFactory.getInstance();

            //setup OAuth Consumer Credentials
            twitter.setOAuthConsumer(settings.getApiKey(), settings.getApiSecret());

            //setup OAuth Access Token
            twitter.setOAuthAccessToken(
                    new AccessToken(settings.getAccessToken(), settings.getAccessTokenSecret()));

            return twitter;
        } catch (Exception e) {
            log.error("Error instantiating Twitter client " + e);
            return null;
        }

    }
}
