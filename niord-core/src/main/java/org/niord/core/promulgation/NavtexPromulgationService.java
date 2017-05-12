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
import org.niord.core.area.Area;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.message.Message;
import org.niord.core.message.MessageSeries;
import org.niord.core.message.MessageSeriesService;
import org.niord.core.message.MessageTokenExpander;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.NavtexMessagePromulgationVo;
import org.niord.core.util.PositionUtils;
import org.niord.core.util.TextUtils;
import org.niord.core.util.TimeUtils;
import org.niord.model.DataFilter;
import org.niord.model.message.Status;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niord.core.util.PositionFormatter.LATLON_NAVTEX;

/**
 * Manages NAVTEX-via-mailing-lists promulgations
 */
@Singleton
@Startup
@Lock(LockType.READ)
@SuppressWarnings("unused")
public class NavtexPromulgationService extends BasePromulgationService {

    public static int NAVTEX_LINE_LENGTH = 40;

    @Inject
    PromulgationTypeService promulgationTypeService;

    @Inject
    MessageSeriesService messageSeriesService;

    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public String getServiceId() {
        return NavtexMessagePromulgation.SERVICE_ID;
    }


    /** {@inheritDoc} */
    @Override
    public String getServiceName() {
        return "NAVTEX mailing list";
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public void onLoadSystemMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        NavtexMessagePromulgationVo navtex = message.promulgation(NavtexMessagePromulgationVo.class, type.getTypeId());
        if (navtex == null) {
            navtex = new NavtexMessagePromulgationVo(type.toVo(DataFilter.get()));
            message.checkCreatePromulgations().add(navtex);
        }

        // Update the preamble from the message series - if defined
        checkNavtexPreamble(message, navtex);

        // Add all active transmitters not already added
        for (NavtexTransmitter transmitter : findTransmittersByAreas(type.getTypeId(), null, true)) {
            if (!navtex.getTransmitters().containsKey(transmitter.getName())) {
                navtex.getTransmitters().put(transmitter.getName(), Boolean.FALSE);
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onCopyMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        NavtexMessagePromulgationVo navtex = message.promulgation(NavtexMessagePromulgationVo.class, type.getTypeId());

        // When copying a message with a NAVTEX promulgation, reset the preamble
        if (navtex != null) {
            navtex.setPreamble(null);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onCreateMessage(Message message, PromulgationType type) throws PromulgationException {
        checkNavtexPromulgation(message, type);
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessage(Message message, PromulgationType type) throws PromulgationException {
        checkNavtexPromulgation(message, type);
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessageStatus(Message message, PromulgationType type) throws PromulgationException {
        NavtexMessagePromulgation navtex = checkNavtexPromulgation(message, type);

        // When the message is published and if the preamble is defined,
        if (message.getStatus() == Status.PUBLISHED && navtex != null) {

            // Update the NAVTEX preamble
            if (StringUtils.isNotBlank(navtex.getPreamble())) {
                // Replace tokens
                navtex.setPreamble(expandNavtexPreambleTokens(
                        navtex.getPreamble(),
                        message.getStatus(),
                        message.getPublishDateFrom(),
                        message.newMessageTokenExpander(null, null)));
            }

            // Update the NAVTEX text
            if (StringUtils.isNotBlank(navtex.getText())) {
                // Format according to NAVTEX guidelines
                String text = TextUtils.maxLineLength(navtex.getText(), NAVTEX_LINE_LENGTH)
                        .toUpperCase()
                        .trim();
                navtex.setText(text);
            }
        }
    }


    /** Check if the NAVTEX preamble should be updated from the message series **/
    private void checkNavtexPreamble(SystemMessageVo message, NavtexMessagePromulgationVo navtex) {
        if (StringUtils.isBlank(navtex.getPreamble()) && message.getMessageSeries() != null) {
            MessageSeries messageSeries = messageSeriesService.findBySeriesId(message.getMessageSeries().getSeriesId());
            if (messageSeries != null && StringUtils.isNotBlank(messageSeries.getNavtexFormat())) {
                navtex.setPreamble(expandNavtexPreambleTokens(
                        messageSeries.getNavtexFormat(),
                        message.getStatus(),
                        message.getPublishDateFrom(),
                        message.newMessageTokenExpander(null, null)));
            }
        }
    }


    /**
     * If the message status is PUBLISHED, CANCELLED or DELETED, expand preamble tokens such as
     * ${publish-date} and ${short-id}
     */
    private String expandNavtexPreambleTokens(String preamble, Status status, Date publishDateFrom, MessageTokenExpander tokenExpander) {
        // Update the NAVTEX preamble
        if (StringUtils.isNotBlank(preamble) && status.isPublic()) {

            // Replace tokens
            preamble = tokenExpander
                    .token("${publish-date}", TimeUtils.formatNavtexDate(publishDateFrom))
                    .expandTokens(preamble);

            // Format according to NAVTEX guidelines
            preamble = TextUtils.maxLineLength(preamble, NAVTEX_LINE_LENGTH)
                    .toUpperCase()
                    .trim();
        }
        return preamble;
    }


    /**
     * Checks that the NAVTEX promulgation is valid and ready to be persisted
     * @param message the message to check
     */
    private NavtexMessagePromulgation checkNavtexPromulgation(Message message, PromulgationType type) {
        NavtexMessagePromulgation navtex = message.promulgation(NavtexMessagePromulgation.class, type.getTypeId());
        if (navtex != null) {
            // Replace the list of transmitters with the persisted entities
            navtex.setTransmitters(persistedTransmitters(type.getTypeId(), navtex.getTransmitters()));

            // Update the preamble from the message series
            if (StringUtils.isBlank(navtex.getPreamble()) && message.getMessageSeries() != null
                    && StringUtils.isNotBlank(message.getMessageSeries().getNavtexFormat())) {
                navtex.setPreamble(message.getMessageSeries().getNavtexFormat());
            }
        }
        return navtex;
    }


    /***************************************/
    /** Generating promulgations          **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public BaseMessagePromulgationVo generateMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {

        NavtexMessagePromulgationVo navtex = new NavtexMessagePromulgationVo(type.toVo(DataFilter.get()));

        // Add all active transmitters - by default, not selected
        findTransmittersByAreas(type.getTypeId(), null, true)
                .forEach(t -> navtex.getTransmitters().put(t.getName(), Boolean.FALSE));

        // Select transmitters associated with the current message areas
        if (message.getAreas() != null && !message.getAreas().isEmpty()) {
            List<Area> areas = message.getAreas().stream()
                    .map(Area::new)
                    .collect(Collectors.toList());
            findTransmittersByAreas(type.getTypeId(), areas, true)
                    .forEach(t -> navtex.getTransmitters().put(t.getName(), Boolean.TRUE));
        }

        String language = getLanguage(type);
        StringBuilder text = new StringBuilder();
        message.getParts().stream()
            .flatMap(p -> p.getDescs().stream())
            .filter(d -> d.getLang().equals(language))
            .filter(d -> StringUtils.isNotBlank(d.getDetails()))
            .map(d -> html2navtex(d.getDetails(), type.getLanguage()))
            .forEach(d -> text.append(d).append(System.lineSeparator()));

        if (text.length() > 0) {
            navtex.setPromulgate(true);
            navtex.setText(text.toString().toUpperCase().trim());
        } else {
            navtex.setPromulgate(false);
        }

        return navtex;
    }


    /** Transforms a HTML description to a NAVTEX description **/
    private String html2navtex(String text, String language) {

        // Replace positions with NAVTEX versions
        text = PositionUtils.replacePositions(app.getLocale(language), LATLON_NAVTEX, text);

        // Remove verbose words, such as "the", from the text
        text = TextUtils.trimHtmlWhitespace(text);
        text = text.replaceAll("(?is)\\s+(the|in pos\\.|is)\\s+", " ");

        // Convert from html to plain text
        text = TextUtils.html2txt(text, true);

        // Split into 40-character lines
        text = TextUtils.maxLineLength(text, NAVTEX_LINE_LENGTH);

        return text.toUpperCase();
    }


    /** {@inheritDoc} */
    @Override
    public void messagePromulgationGenerated(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        NavtexMessagePromulgationVo navtex = message.promulgation(NavtexMessagePromulgationVo.class, type.getTypeId());
        if (navtex != null) {

            if (StringUtils.isNotBlank(navtex.getText())) {
                String text = navtex.getText();

                // Split into 40-character lines, remove blank lines and enforce uppercase
                text = TextUtils.maxLineLength(text, NAVTEX_LINE_LENGTH)
                        .replaceAll("(?is)\\n+", "\n")
                        .toUpperCase()
                        .trim();

                navtex.setText(text);
            }

            // Compute the active transmitters from the message areas
            if (message.getAreas() != null && !message.getAreas().isEmpty()) {
                List<Area> areas = message.getAreas().stream()
                        .map(Area::new)
                        .collect(Collectors.toList());
                String typeId = type.getTypeId();
                Set<String> enabledTransmitters = findTransmittersByAreas(typeId, areas, true).stream()
                        .map(NavtexTransmitter::getName)
                        .collect(Collectors.toSet());
                navtex.getTransmitters().keySet().forEach(name ->
                        navtex.getTransmitters().put(name, enabledTransmitters.contains(name)));
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void resetMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        NavtexMessagePromulgationVo navtex = message.promulgation(NavtexMessagePromulgationVo.class, type.getTypeId());
        if (navtex != null) {
            navtex.reset();
            checkNavtexPreamble(message, navtex);
            findTransmittersByAreas(type.getTypeId(), null, true)
                    .forEach(t -> navtex.getTransmitters().put(t.getName(), Boolean.FALSE));
        }
    }


    /***************************************/
    /** Transmitter Handling              **/
    /***************************************/


    /** Returns the transmitter with the given name, or null if not found **/
    public NavtexTransmitter findTransmitterByName(String typeId, String name) {
        try {
            return em.createNamedQuery("NavtexTransmitter.findByName", NavtexTransmitter.class)
                    .setParameter("typeId", typeId)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /** Update the list of transmitters with the persisted entities **/
    protected List<NavtexTransmitter> persistedTransmitters(String typeId, List<NavtexTransmitter> transmitters) {
        return transmitters.stream()
                .map(t -> findTransmitterByName(typeId, t.getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * Find all NAVTEX transmitters matching the given areas.
     * @param areas the areas to match
     * @return all NAVTEX transmitters matching the given areas.
     */
    @SuppressWarnings("all")
    public List<NavtexTransmitter> findTransmittersByAreas(String typeId, List<Area> areas, boolean onlyActive) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<NavtexTransmitter> query = cb.createQuery(NavtexTransmitter.class);
        Root<NavtexTransmitter> transmitterRoot = query.from(NavtexTransmitter.class);

        CriteriaHelper<NavtexTransmitter> criteriaHelper = new CriteriaHelper<>(cb, query);

        if (onlyActive) {
            criteriaHelper.equals(transmitterRoot.get("active"), Boolean.TRUE);
        }

        if (StringUtils.isNotBlank(typeId)) {
            Join<NavtexTransmitter, PromulgationType> typeJoin = transmitterRoot.join("promulgationType", JoinType.LEFT);
            criteriaHelper.equals(typeJoin.get("typeId"), typeId);
        }

        if (areas != null && !areas.isEmpty()) {

            // Make sure we have managed entities, not template antities
            areas = persistedList(Area.class, areas);

            Join<NavtexTransmitter, Area> areaJoin = transmitterRoot.join("areas", JoinType.LEFT);
            Predicate[] areaMatch = areas.stream()
                    .map(a -> cb.equal(cb.locate(cb.literal(a.getLineage()), areaJoin.get("lineage")), 1))
                    .toArray(Predicate[]::new);
            criteriaHelper.add(cb.or(areaMatch));
        }

        query.select(transmitterRoot)
                .where(criteriaHelper.where())
                .orderBy(cb.asc(cb.lower(transmitterRoot.get("name"))));

        return em.createQuery(query).getResultList();
    }


    /** Returns all transmitters associated with the given NAVTEX promulgation type */
    public List<NavtexTransmitter> getTransmitters(String typeId) {
        return em.createNamedQuery("NavtexTransmitter.findByType", NavtexTransmitter.class)
                .setParameter("typeId", typeId)
                .getResultList();
    }


    /** Creates a new transmitter */
    public NavtexTransmitter createTransmitter(NavtexTransmitter transmitter) {

        String typeId = transmitter.getPromulgationType().getTypeId();
        log.info("Creating transmitter " + transmitter.getName() + " for promulgation type " + typeId);

        transmitter.setPromulgationType(promulgationTypeService.getPromulgationType(typeId));
        transmitter.setAreas(persistedList(Area.class, transmitter.getAreas()));
        return saveEntity(transmitter);
    }


    /** Updates an existing transmitter */
    public NavtexTransmitter updateTransmitter(NavtexTransmitter transmitter) {

        String typeId = transmitter.getPromulgationType().getTypeId();
        log.info("Updating transmitter " + transmitter.getName() + " for promulgation type " + typeId);

        NavtexTransmitter original = findTransmitterByName(typeId, transmitter.getName());
        original.setActive(transmitter.isActive());
        original.setAreas(persistedList(Area.class, transmitter.getAreas()));
        return saveEntity(original);
    }


    /** Deletes an existing transmitter */
    @SuppressWarnings("all")
    public boolean deleteTransmitter(String typeId, String name) {

        log.info("Deleting transmitter " + name + " for promulgation type " + typeId);

        NavtexTransmitter original = findTransmitterByName(typeId, name);
        if (original != null) {
            remove(original);
            return true;
        }
        return false;
    }

}
