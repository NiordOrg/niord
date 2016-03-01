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
package org.niord.core.geojson;

import org.niord.core.model.BaseEntity;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.niord.model.vo.geojson.FeatureVo;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a GeoJson feature collection entity
 */
@Entity
@NamedQueries({
        @NamedQuery(name  = "FeatureCollection.findByUid",
                query = "select fc from FeatureCollection fc where fc.uid = :uid")
})
@SuppressWarnings("unused")
public class FeatureCollection extends BaseEntity<Integer> {

    @Column(unique = true)
    String uid;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "featureCollection", orphanRemoval = true)
    private List<Feature> features = new ArrayList<>();

    /** Converts this FeatureCollection entity to GeoJson */
    public FeatureCollectionVo toGeoJson() {
        FeatureCollectionVo fc = new FeatureCollectionVo();
        fc.setFeatures(features.stream()
                .map(Feature::toGeoJson)
                .toArray(FeatureVo[]::new));
        fc.setId(uid);
        return fc;
    }

    /** Converts a GeoJson FeatureCollection to a FeatureCollection entity */
    public static FeatureCollection fromGeoJson(FeatureCollectionVo fc) {
        FeatureCollection featureCollection = new FeatureCollection();
        if (fc.getId() != null) {
            featureCollection.setUid(fc.getId().toString());
        }
        if (fc.getFeatures() != null) {
            for (FeatureVo f : fc.getFeatures()) {
                Feature feature = Feature.fromGeoJson(f);
                featureCollection.getFeatures().add(feature);
                feature.setFeatureCollection(featureCollection);
            }
        }
        return featureCollection;
    }

    /** Ensure that the UID is defined */
    @PrePersist
    protected void onCreate() {
        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }
}
