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

import io.quarkus.arc.Lock;
import org.apache.commons.lang.StringUtils;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.promulgation.PromulgationType.Requirement;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.promulgation.vo.SafetyNetAreaVo;
import org.niord.core.promulgation.vo.SafetyNetMessagePromulgationVo;
import org.niord.core.util.PositionAssembler;
import org.niord.core.util.PositionUtils;
import org.niord.core.util.TextUtils;
import org.niord.model.DataFilter;
import org.niord.model.message.Type;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages SafetyNET-via-mailing-lists promulgations
 */
@ApplicationScoped
@Lock(Lock.Type.READ)
@SuppressWarnings("unused")
public class SafetyNetPromulgationService
        extends BasePromulgationService {

    public static final String SUPERFLUOUS_WORDS = "the|in pos\\.|is";

    @Inject
    PromulgationTypeService promulgationTypeService;

    /***************************************/
    /** Promulgation Service Handling     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public String getServiceId() {
        return SafetyNetMessagePromulgation.SERVICE_ID;
    }


    /** {@inheritDoc} */
    @Override
    public String getServiceName() {
        return "SafetyNET mailing list";
    }


    /***************************************/
    /** Message Life-cycle Management     **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public void onLoadSystemMessage(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        SafetyNetMessagePromulgationVo safetynet = message.promulgation(SafetyNetMessagePromulgationVo.class, type.getTypeId());
        if (safetynet == null) {
            safetynet = new SafetyNetMessagePromulgationVo(type.toVo(DataFilter.get()));
            message.checkCreatePromulgations().add(safetynet);
        }

        checkSafetyNetArea(safetynet, message.getType());
    }


    /** {@inheritDoc} */
    @Override
    public void onCreateMessage(Message message, PromulgationType type) throws PromulgationException {
        checkSafetyNetPromulgation(message, type);
    }


    /** {@inheritDoc} */
    @Override
    public void onUpdateMessage(Message message, PromulgationType type) throws PromulgationException {
        checkSafetyNetPromulgation(message, type);
    }


    /**
     * Checks that the SafetyNET promulgation is valid and ready to be persisted
     * @param message the message to check
     */
    private void checkSafetyNetPromulgation(Message message, PromulgationType type) {
        SafetyNetMessagePromulgation safetynet = message.promulgation(SafetyNetMessagePromulgation.class, type.getTypeId());
        if (safetynet != null && safetynet.getArea() != null) {
            // Replace the area with the persisted entities
            safetynet.setArea(findAreaByName(type.getTypeId(), safetynet.getArea().getName()));
        }
    }


    /**
     * Checks that SafetyNET area matches the current message type.
     * @param safetynet the promulgation type.
     * @param messageType the message type to match
     */
    public void checkSafetyNetArea(SafetyNetMessagePromulgationVo safetynet, Type messageType) {

        // Get all active areas for the given message type
        List<SafetyNetAreaVo> areas = findAreasByMessageType(safetynet.getType().getTypeId(), messageType, true).stream()
                .map(a -> a.toVo(DataFilter.get()))
                .collect(Collectors.toList());
        safetynet.setAreas(areas);

        // Get the selected area - found by matching the selected area name against the list of areas
        SafetyNetAreaVo selArea = safetynet.selectedArea();

        // If no selected area was found (it may e.g. be inactive), look it up by name and add it to the area list.
        if (selArea == null && StringUtils.isNotBlank(safetynet.getAreaName())) {
            selArea = findAreaByName(safetynet.getType().getTypeId(), safetynet.getAreaName()).toVo(DataFilter.get());
            // For persisted SafetyNET promulgations, always include the selected area, even if it is not valid anymore
            if (safetynet.getId() != null && selArea != null) {
                safetynet.getAreas().add(selArea);
            } else {
                safetynet.setAreaName(null);
            }
        }

        // If the SafetyNET promulgation is not yet persisted and no area is specified
        // and only one area is available, select it.
        if (StringUtils.isBlank(safetynet.getAreaName()) && areas.size() == 1) {
            safetynet.setAreaName(areas.get(0).getName());
        }
    }


    /***************************************/
    /** Generating promulgations          **/
    /***************************************/


    /** {@inheritDoc} */
    @Override
    public BaseMessagePromulgationVo generateMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {

        SafetyNetMessagePromulgationVo safetynet = new SafetyNetMessagePromulgationVo(type.toVo(DataFilter.get()));

        // Compute the SafetyNET text
        String language = getLanguage(type);
        StringBuilder text = new StringBuilder();
        message.getParts().stream()
            .flatMap(p -> p.getDescs().stream())
            .filter(d -> d.getLang().equals(language))
            .filter(d -> StringUtils.isNotBlank(d.getDetails()))
            .map(d -> html2safetynet(d.getDetails(), type.getLanguage()))
            .forEach(d -> text.append(d).append(System.lineSeparator()));

        if (text.length() > 0) {
            safetynet.setPromulgate(true);
            safetynet.setText(text.toString().toUpperCase().trim());
        } else {
            safetynet.setPromulgate(type.getRequirement() == Requirement.MANDATORY);
        }

        return safetynet;
    }


    /** Transforms a HTML description to a SafetyNET description **/
    private String html2safetynet(String text, String language) {

        // Convert from html to plain text
        text = TextUtils.html2txt(text, true);

        // Remove separator between positions
        text = PositionUtils.replaceSeparator(text, " ");

        // Replace positions with NAVTEX versions
        PositionAssembler navtexPosAssembler = PositionAssembler.newNavtexPositionAssembler();
        text = PositionUtils.updatePositionFormat(text, navtexPosAssembler);

        // Remove verbose words, such as "the", from the text
        text = TextUtils.removeWords(text, SUPERFLUOUS_WORDS);

        // NB: unlike NAVTEX, we do not split into 40-character lines

        return text.toUpperCase();
    }


    /** {@inheritDoc} */
    @Override
    public void messagePromulgationGenerated(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        SafetyNetMessagePromulgationVo safetynet = message.promulgation(SafetyNetMessagePromulgationVo.class, type.getTypeId());
        if (safetynet != null) {

            if (StringUtils.isNotBlank(safetynet.getText())) {
                String text = safetynet.getText();

                // Remove blank lines and enforce uppercase
                text = text
                        .replaceAll("(?is)\\n+", "\n")
                        .toUpperCase()
                        .trim();

                safetynet.setText(text);
            }

            // Compute the SafetyNET distribution area
            checkSafetyNetArea(safetynet, message.getType());
        }
    }


    /** {@inheritDoc} */
    @Override
    public void resetMessagePromulgation(SystemMessageVo message, PromulgationType type) throws PromulgationException {
        SafetyNetMessagePromulgationVo safetynet = message.promulgation(SafetyNetMessagePromulgationVo.class, type.getTypeId());
        if (safetynet != null) {
            safetynet.reset();
            checkSafetyNetArea(safetynet, message.getType());
        }
    }


    /***************************************/
    /** SafetyNET Area Handling           **/
    /***************************************/


    /** Returns the area with the given name, or null if not found **/
    public SafetyNetArea findAreaByName(String typeId, String name) {
        try {
            return em.createNamedQuery("SafetyNetArea.findByName", SafetyNetArea.class)
                    .setParameter("typeId", typeId)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Find all SafetyNET areas matching the given message type.
     * @param typeId the promulgation type
     * @param messageType the message type to match
     * @param onlyActive whether or not to only return active areas
     * @return all SafetyNET areas matching the given message type.
     */
    public List<SafetyNetArea> findAreasByMessageType(String typeId, Type messageType, boolean onlyActive) {

        return em.createNamedQuery("SafetyNetArea.findByType", SafetyNetArea.class)
                .setParameter("typeId", typeId)
                .getResultList()
                .stream()
                .filter(a -> !onlyActive || a.isActive())
                .filter(a -> a.supportsMessageType(messageType))
                .collect(Collectors.toList());
    }


    /** Returns all SafetyNET areas associated with the given SafetyNET promulgation type */
    public List<SafetyNetArea> getAreas(String typeId) {
        return em.createNamedQuery("SafetyNetArea.findByType", SafetyNetArea.class)
                .setParameter("typeId", typeId)
                .getResultList();
    }


    /** Creates a new SafetyNET area */
    @Transactional
    public SafetyNetArea createArea(SafetyNetArea area) {

        String typeId = area.getPromulgationType().getTypeId();
        log.info("Creating SafetyNET area " + area.getName() + " for promulgation type " + typeId);

        area.setPromulgationType(promulgationTypeService.getPromulgationType(typeId));
        return saveEntity(area);
    }


    /** Updates an existing SafetyNET area */
    @Transactional
    public SafetyNetArea updateArea(SafetyNetArea area) {

        String typeId = area.getPromulgationType().getTypeId();
        log.info("Updating SafetyNET area " + area.getName() + " for promulgation type " + typeId);

        SafetyNetArea original = findAreaByName(typeId, area.getName());
        original.update(area);

        return saveEntity(original);
    }


    /** Deletes an existing SafetyNET area */
    @SuppressWarnings("all")
    @Transactional
    public boolean deleteArea(String typeId, String name) {

        log.info("Deleting SafetyNET area " + name + " for promulgation type " + typeId);

        SafetyNetArea original = findAreaByName(typeId, name);
        if (original != null) {
            remove(original);
            return true;
        }
        return false;
    }

}
