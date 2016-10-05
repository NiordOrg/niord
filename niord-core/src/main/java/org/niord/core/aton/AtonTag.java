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
package org.niord.core.aton;

import org.hibernate.search.annotations.Field;
import org.niord.core.aton.vo.AtonTagVo;
import org.niord.core.model.BaseEntity;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 * An AtoN OSM seamark node tag entity.
 *
 * The AtoN model adheres to the OSM seamark specification, please refer to:
 * http://wiki.openstreetmap.org/wiki/Key:seamark
 * and sub-pages.
 */
@Entity
@Table(indexes = {
        @Index(name = "aton_tag_k", columnList="k"),
        @Index(name = "aton_tag_v", columnList="v")
})
@SuppressWarnings("unused")
public class AtonTag extends BaseEntity<Integer> {

    // Custom AtoN tags
    public static final String TAG_ATON_UID         = "seamark:ref";
    public static final String TAG_LIGHT_NUMBER     = "seamark:light:ref";
    public static final String TAG_INT_LIGHT_NUMBER = "seamark:light:int_ref";
    public static final String TAG_LOCALITY         = "seamark:locality";
    public static final String TAG_AIS_NUMBER       = "seamark:ais:ref";
    public static final String TAG_RACON_NUMBER     = "seamark:racon:ref";
    public static final String TAG_INT_RACON_NUMBER = "seamark:racon:int_ref";

    @NotNull
    String k;

    @Field
    String v;

    @ManyToOne
    @NotNull
    AtonNode atonNode;

    /** Constructor */
    public AtonTag() {
    }

    /** Constructor */
    public AtonTag(String k, String v) {
        this.k = k;
        this.v = v;
    }

    /** Constructor */
    public AtonTag(AtonTagVo tag, AtonNode atonNode) {
        Objects.requireNonNull(tag);
        this.k = tag.getK();
        this.v = tag.getV();
        this.atonNode = atonNode;
    }

    /** Converts this entity to a value object */
    public AtonTagVo toVo() {
        return new AtonTagVo(k, v);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getK() {
        return k;
    }

    public void setK(String k) {
        this.k = k;
    }

    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }

    public AtonNode getAtonNode() {
        return atonNode;
    }

    public void setAtonNode(AtonNode atonNode) {
        this.atonNode = atonNode;
    }
}
