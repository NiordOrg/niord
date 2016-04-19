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
package org.niord.model.vo.geojson;


import io.swagger.annotations.ApiModel;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.function.Consumer;

/**
 * GeoJSON FeatureCollection, as defined in the specification:
 * hhttp://geojson.org/geojson-spec.html#feature-objects
 */
@ApiModel(value = "FeatureCollection", description = "GeoJson FeatureCollection type")
@XmlRootElement(name = "featureCollection")
public class FeatureCollectionVo extends GeoJsonVo {

    // NB: "id" not directly mentioned in specification, but it's useful...
    private Object id;
    private FeatureVo[] features;

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        if (features != null) {
            for (FeatureVo feature : features) {
                feature.visitCoordinates(handler);
            }
        }
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @XmlElementRef
    public FeatureVo[] getFeatures() {
        return features;
    }

    public void setFeatures(FeatureVo[] features) {
        this.features = features;
    }
}

