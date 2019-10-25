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

package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;
import org.niord.model.geojson.FeatureCollectionVo;

import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * Value object for the {@code MessagePart} model entity
 */
@ApiModel(value = "MessagePart", description = "Main NW and NM message part class")
@XmlType(propOrder = {
        "indexNo", "type", "eventDates", "geometry", "descs", "hideSubject"
})
@SuppressWarnings("unused")
public class MessagePartVo implements ILocalizable<MessagePartDescVo>, IJsonSerializable {

    /**
     * The Index no.
     */
    int indexNo;
    /**
     * The Type.
     */
    MessagePartType type;
    /**
     * The Event dates.
     */
    List<DateIntervalVo> eventDates;
    /**
     * The Geometry.
     */
    FeatureCollectionVo geometry;
    /**
     * The Descs.
     */
    List<MessagePartDescVo> descs;
    /**
     * The Hide subject.
     */
    Boolean hideSubject;


    /**
     * Returns a filtered copy of this entity  @param filter the filter
     *
     * @return the message part vo
     */
    public MessagePartVo copy(DataFilter filter) {

        DataFilter compFilter = filter.forComponent("MessagePart");

        MessagePartVo part = new MessagePartVo();
        part.setType(type);
        if (compFilter.includeDetails()) {
            if (eventDates != null) {
                eventDates.forEach(d  -> part.checkCreateEventDates().add(d.copy()));
            }
            part.setDescs(getDescs(compFilter));
            part.setHideSubject(hideSubject);
        }
        if (compFilter.includeGeometry() && geometry != null) {
            // TODO: Deep-copy the Geometry
            part.setGeometry(geometry);
        }
        return part;
    }


    /** {@inheritDoc} */
    @Override
    public MessagePartDescVo createDesc(String lang) {
        MessagePartDescVo desc = new MessagePartDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /**
     * Rewrites the rich text description from one repository path to another.
     * This happens when a message is edited and its associated repository folder gets
     * copied to a temporary folder until the message is saved.
     * <p>
     * The repository paths may occur in e.g. embedded images and links.
     *
     * @param repoPath1 the repo path 1
     * @param repoPath2 the repo path 2
     */
    public void rewriteRepoPath(String repoPath1, String repoPath2) {
        if (getDescs() != null) {
            getDescs().forEach(desc -> {
                if (desc.getDetails() != null && desc.getDetails().contains(repoPath1)) {
                    desc.setDetails(desc.getDetails().replace(repoPath1, repoPath2));
                }
            });
        }
    }


    /**
     * Check create event dates list.
     *
     * @return the list
     */
    public List<DateIntervalVo> checkCreateEventDates() {
        if (eventDates == null) {
            eventDates = new ArrayList<>();
        }
        return eventDates;
    }



    /*************************/
    /** Getters and Setters **/
    /**
     * Gets type.
     *
     * @return the type
     */

    public MessagePartType getType() {
        return type;
    }

    /**
     * Sets type.
     *
     * @param type the type
     */
    public void setType(MessagePartType type) {
        this.type = type;
    }

    /**
     * Gets index no.
     *
     * @return the index no
     */
    public int getIndexNo() {
        return indexNo;
    }

    /**
     * Sets index no.
     *
     * @param indexNo the index no
     */
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    /**
     * Gets event dates.
     *
     * @return the event dates
     */
    public List<DateIntervalVo> getEventDates() {
        return eventDates;
    }

    /**
     * Sets event dates.
     *
     * @param eventDates the event dates
     */
    public void setEventDates(List<DateIntervalVo> eventDates) {
        this.eventDates = eventDates;
    }

    /**
     * Gets geometry.
     *
     * @return the geometry
     */
    public FeatureCollectionVo getGeometry() {
        return geometry;
    }

    /**
     * Sets geometry.
     *
     * @param geometry the geometry
     */
    public void setGeometry(FeatureCollectionVo geometry) {
        this.geometry = geometry;
    }

    @Override
    public List<MessagePartDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessagePartDescVo> descs) {
        this.descs = descs;
    }

    /**
     * Gets hide subject.
     *
     * @return the hide subject
     */
    public Boolean getHideSubject() {
        return hideSubject;
    }

    /**
     * Sets hide subject.
     *
     * @param hideSubject the hide subject
     */
    public void setHideSubject(Boolean hideSubject) {
        this.hideSubject = hideSubject;
    }
}
