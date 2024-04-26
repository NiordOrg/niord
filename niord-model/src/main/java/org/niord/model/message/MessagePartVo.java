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

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;
import org.niord.model.geojson.FeatureCollectionVo;

import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * Value object for the {@code MessagePart} model entity
 */
@Schema(name = "MessagePart", description = "Main NW and NM message part class")
@XmlType(propOrder = {
        "indexNo", "type", "eventDates", "geometry", "descs", "hideSubject"
})
@SuppressWarnings("unused")
public class MessagePartVo implements ILocalizable<MessagePartDescVo>, IJsonSerializable {

    int indexNo;
    MessagePartType type;
    List<DateIntervalVo> eventDates;
    FeatureCollectionVo geometry;
    List<MessagePartDescVo> descs;
    Boolean hideSubject;


    /** Returns a filtered copy of this entity **/
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


    public List<DateIntervalVo> checkCreateEventDates() {
        if (eventDates == null) {
            eventDates = new ArrayList<>();
        }
        return eventDates;
    }



    /*************************/
    /** Getters and Setters **/
    /*************************/

    public MessagePartType getType() {
        return type;
    }

    public void setType(MessagePartType type) {
        this.type = type;
    }

    public int getIndexNo() {
        return indexNo;
    }

    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public List<DateIntervalVo> getEventDates() {
        return eventDates;
    }

    public void setEventDates(List<DateIntervalVo> eventDates) {
        this.eventDates = eventDates;
    }

    public FeatureCollectionVo getGeometry() {
        return geometry;
    }

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

    public Boolean getHideSubject() {
        return hideSubject;
    }

    public void setHideSubject(Boolean hideSubject) {
        this.hideSubject = hideSubject;
    }
}
