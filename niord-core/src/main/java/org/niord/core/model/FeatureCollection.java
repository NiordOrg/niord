package org.niord.core.model;

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
public class FeatureCollection extends BaseEntity {

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
