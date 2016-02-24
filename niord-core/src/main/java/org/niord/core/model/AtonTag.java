/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.model;

import org.niord.model.vo.aton.AtonTagVo;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
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
@SuppressWarnings("unused")
public class AtonTag extends BaseEntity<Integer> {

    // Custom AtoN tags
    public static final String CUST_TAG_ATON_UID            = "seamark_x:aton_uid";
    public static final String CUST_TAG_LIGHT_NUMBER        = "seamark_x:light_number";
    public static final String CUST_TAG_INT_LIGHT_NUMBER    = "seamark_x:int_light_number";

    @NotNull
    String k;

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
