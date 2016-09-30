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

package org.niord.core.message;

import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.MessagePartDescVo;
import org.niord.model.message.MessagePartVo;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;


/**
 * The core message part entity
 */
@Entity
@SuppressWarnings("unused")
public class MessagePart extends BaseEntity<Integer> implements ILocalizable<MessagePartDesc>, IndexedEntity {

    @NotNull
    @ManyToOne
    Message message;

    int indexNo;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    FeatureCollection geometry;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MessagePartDesc> descs = new ArrayList<>();

    /**
     * Constructor
     */
    public MessagePart() {
    }


    /**
     * Constructor
     */
    public MessagePart(MessagePartVo part) {
        this(part, DataFilter.get());
    }


    /**
     * Constructor
     */
    public MessagePart(MessagePartVo part, DataFilter filter) {

        // NB: indexNo automatically assigned
        if (part.getGeometry() != null) {
            this.geometry = FeatureCollection.fromGeoJson(part.getGeometry());
        }
        if (part.getDescs() != null) {
            part.getDescs().stream()
                    .filter(MessagePartDescVo::descDefined)
                    .forEach(desc -> {
                        MessagePartDesc d = createDesc(desc.getLang());
                        d.setSubject(desc.getSubject());
                        d.setDetails(desc.getDetails());
                    });
        }
    }


    /**
     * Updates this entity from the message part according to the filter
     */
    public void updateMessagePart(MessagePart part) {

        this.indexNo = part.getIndexNo();
        copyDescsAndRemoveBlanks(part.getDescs());
    }


    /** Converts this entity to a value object */
    private MessagePartVo toVo(MessagePartVo part, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(MessagePart.class);

        if (compFilter.includeDetails()) {
            getDescs(compFilter).forEach(desc -> part.checkCreateDescs().add(desc.toVo(compFilter)));
        }
        if (compFilter.includeGeometry() && geometry != null) {
            part.setGeometry(geometry.toGeoJson());
            GeoJsonUtils.setLanguage(part.getGeometry(), compFilter.getLang(), false);
        }

        return part;
    }


    /** Converts this entity to a value object */
    public MessagePartVo toVo(DataFilter filter) {
        return toVo(new MessagePartVo(), filter);
    }


    /** Returns if the message part contains any data **/
    public boolean partDefined() {
        descs.removeIf(d -> !d.descDefined());
        return !descs.isEmpty() || geometry != null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MessagePartDesc createDesc(String lang) {
        MessagePartDesc desc = new MessagePartDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a description entity to this message */
    public void addDesc(MessagePartDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public FeatureCollection getGeometry() {
        return geometry;
    }

    public void setGeometry(FeatureCollection geometry) {
        this.geometry = geometry;
    }

    @Override
    public List<MessagePartDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessagePartDesc> descs) {
        this.descs = descs;
    }
}
