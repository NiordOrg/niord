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
package org.niord.core.geojson;

import org.niord.core.model.BaseEntity;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.FeatureVo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a GeoJson feature collection entity
 */
@Entity
@Table(indexes = {
        @Index(name = "feature_collection_uid", columnList="uid", unique = true)
})
@NamedQueries({
        @NamedQuery(name  = "FeatureCollection.findByUid",
                query = "select fc from FeatureCollection fc where fc.uid = :uid")
})
@SuppressWarnings("unused")
public class FeatureCollection extends BaseEntity<Integer> {

    @Column(unique = true)
    String uid;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "featureCollection", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
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


    /** Assigns a new UID to the Feature **/
    public String assignNewUid() {
        uid = UUID.randomUUID().toString();
        return uid;
    }


    /** Ensure that the UID is defined */
    @PrePersist
    protected void onCreate() {
        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }
    }


    /** Adds a new feature to the collection */
    public void addFeature(Feature feature) {
        feature.setFeatureCollection(this);
        features.add(feature);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

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
