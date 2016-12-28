/*
 * Copyright 2016 Danish Maritime Authority.
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

package org.niord.core.publication;

import org.apache.commons.lang.StringUtils;
import org.niord.core.message.MessageTag;
import org.niord.core.message.MessageTagService;
import org.niord.core.util.TimeUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.niord.core.message.vo.MessageTagVo.MessageTagType.PUBLIC;
import static org.niord.core.publication.Publication.DEFAULT_EDITION;

/**
 * Helper class used when updating a publication from its template.
 * An instance of this class is only valid within a single transaction.
 * <p>
 * Primarily, the class has utility methods for expanding the parameters
 * of a value with the format ${week}, ${year}, etc.
 * <p>
 * The class also sports a function for looking up or creating a message tag to associate with the publication.
 */
public class PublicationTemplateUpdateCtx {

    final Publication publication;
    final MessageTagService messageTagService;
    final Map<String, String> replacementValues = new HashMap<>();

    /** Constructor **/
    public PublicationTemplateUpdateCtx(Publication publication, MessageTagService messageTagService) {
        this.publication = publication;
        this.messageTagService = messageTagService;

        String edition = publication.getEdition() != null
                ? String.valueOf(publication.getEdition())
                : String.valueOf(DEFAULT_EDITION);
        replacementValues.put("${edition}", edition);

        Date date = publication.getPublishDateFrom();
        if (date != null) {
            int year = TimeUtils.getCalendarField(date, Calendar.YEAR);
            int week = TimeUtils.getISO8601WeekOfYear(date);
            // NB: Month starts with 0 = January, which is not very usable for UI
            int month = TimeUtils.getCalendarField(date, Calendar.MONTH) + 1;

            replacementValues.put("${year-2-digits}", String.valueOf(year).substring(2));
            replacementValues.put("${year}", String.valueOf(year));
            replacementValues.put("${week}", String.valueOf(week));
            replacementValues.put("${week-2-digits}", String.format("%02d", week));
            replacementValues.put("${month}", String.valueOf(month));
            replacementValues.put("${month-2-digits}", String.format("%02d", month));
        }
    }

    /**
     * Finds or creates a public tag with the given name
     * @param name the tag name
     * @return the tag
     */
    public MessageTag findOrCreatePublicMessageTag(String name) {
        List<MessageTag> tags = messageTagService.findTagsByTypeAndName(PUBLIC, name);
        if (tags.isEmpty()) {
            MessageTag tag = new MessageTag();
            tag.checkAssignTagId();
            tag.setName(name);
            tag.setType(PUBLIC);
            tag.setLocked(false);
            return messageTagService.saveEntity(tag);
        } else {
            return tags.get(0);
        }
    }

    /** Returns the value, or the default value if the value is null **/
    public <D> D val(D value, D defaultValue) {
        return value != null  ? value : defaultValue;
    }


    /**
     * Expand the parameters of a value with the format ${week}, ${year}, etc.
     * @param value the value to expand parameters for
     * @return the updated value
     */
    public String str(String value, String defaultValue) {
        if (!replacementValues.isEmpty() && StringUtils.isNotBlank(value)) {
            for (Map.Entry<String, String> kv : replacementValues.entrySet()) {
                value = value.replace(kv.getKey(), kv.getValue());
            }
        } else if (StringUtils.isNotBlank(defaultValue)) {
            value = defaultValue;
        }
        return value;
    }

}
