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

import org.locationtech.jts.geom.Geometry;
import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.geojson.FeatureVo;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a GeoJson feature entity
 * <p>
 * TODO
 * Create index using "alter table Feature add spatial index feature_index (geometry);"
 */
@Entity
@Table(indexes = {
        @Index(name = "feature_uid", columnList="uid", unique = true)
})
@NamedQueries({
        @NamedQuery(name  = "Feature.findByUid",
                query = "select f from Feature f where f.uid = :uid"),
        @NamedQuery(name  = "Feature.hits",
                query = "select f from Feature f where within(:pt, f.geometry) = true")
})
@SuppressWarnings("unused")
public class Feature extends BaseEntity<Integer> implements IndexedEntity {
    public final static int WGS84_SRID = 4326;

    @Column(unique = true)
    String uid;

    @ManyToOne
    @NotNull
    FeatureCollection featureCollection;

    int indexNo;

    @Column(columnDefinition = "GEOMETRY", nullable = false)
    private Geometry geometry;

    @Column(name="properties", columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    private Map<String, Object> properties = new HashMap<>();

    /** Ensure that the UID is defined */
    @PrePersist
    protected void onCreate() {
        if (uid == null) {
            assignNewUid();
        }
    }

    /** Assigns a new UID to the Feature **/
    public String assignNewUid() {
        uid = UUID.randomUUID().toString();
        return uid;
    }

    /** Converts this Feature entity to GeoJson */
    public FeatureVo toGeoJson() {
        FeatureVo vo = new FeatureVo();
        vo.setGeometry(JtsConverter.fromJts(geometry));
        vo.setId(uid);
        vo.getProperties().putAll(properties);
        return vo;
    }

    /** Converts a GeoJson Feature to a Feature entity */
    public static Feature fromGeoJson(FeatureVo vo) {
        Feature feature = new Feature();
        if (vo.getId() != null) {
            feature.setUid(vo.getId().toString());
        }
        feature.getProperties().putAll(vo.getProperties());
        feature.setGeometry(JtsConverter.toJts(vo.getGeometry()));
        return feature;
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

    public FeatureCollection getFeatureCollection() {
        return featureCollection;
    }

    public void setFeatureCollection(FeatureCollection featureCollection) {
        this.featureCollection = featureCollection;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        if (geometry != null) {
            geometry.setSRID(WGS84_SRID);
        }
        this.geometry = geometry;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
