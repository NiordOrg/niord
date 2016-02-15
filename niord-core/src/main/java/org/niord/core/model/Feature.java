package org.niord.core.model;

import com.vividsolutions.jts.geom.Geometry;
import org.niord.model.vo.geojson.FeatureVo;
import org.niord.core.util.GeoJsonUtils;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.PrePersist;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a GeoJson feature entity
 * <p>
 * TODO
 * Create index using "alter table Feature add spatial index feature_index (geometry);"
 */
@Entity
@NamedQueries({
        @NamedQuery(name  = "Feature.findByUid",
                query = "select f from Feature f where f.uid = :uid"),
        @NamedQuery(name  = "Feature.hits",
                query = "select f from Feature f where within(:pt, f.geometry) = true")
})
public class Feature extends BaseEntity {
    public final static int WGS84_SRID = 4326;

    @Column(unique = true)
    String uid;

    @ManyToOne
    @NotNull
    FeatureCollection featureCollection;

    @Column(columnDefinition = "GEOMETRY", nullable = false)
    private Geometry geometry;

    @ElementCollection
    @JoinTable(name="FeatureProperties", joinColumns=@JoinColumn(name="id"))
    @MapKeyColumn(name="name")
    @Column(name="value")
    private Map<String, String> properties = new HashMap<>();

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
        vo.setGeometry(GeoJsonUtils.fromJts(geometry));
        vo.setId(uid);
        FeaturePropertiesHandler.copyToVo(properties, vo.getProperties());
        return vo;
    }

    /** Converts a GeoJson Feature to a Feature entity */
    public static Feature fromGeoJson(FeatureVo vo) {
        Feature feature = new Feature();
        if (vo.getId() != null) {
            feature.setUid(vo.getId().toString());
        }
        FeaturePropertiesHandler.copyFromVo(feature.getProperties(), vo.getProperties());
        feature.setGeometry(GeoJsonUtils.toJts(vo.getGeometry()));
        return feature;
    }

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

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        if (geometry != null) {
            geometry.setSRID(WGS84_SRID);
        }
        this.geometry = geometry;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}


/**
 * Feature properties could have been handled in many ways, e.g. saved wholly or partly encoded as JSON
 * or we could have implemented a FeatureProperties table with typed values. Except that a property
 * value may be another json object, etc.
 * <p>
 * However, in Niord we only support a small set of simple properties, so,  FeatureProperties is implemented
 * as a simple string-based name-value table. It still allow us to join over and make interesting queries, unlike
 * if the properties had been persisted as JSON.
 */
class FeaturePropertiesHandler {
    final static List<String> PROPERTY_NAME_PREFIXES =
            Arrays.asList("name", "restriction",
                    "bufferRadius", "bufferRadiusType", "parentFeatureId"); // TODO AtoN

    final static Set<String> NUMERIC_PROPERTIES =
            new HashSet<>(Arrays.asList("bufferRadius"));

    /** Checks that the property name is suppoerted **/
    public static boolean supportedProperty(String name) {
        if (name != null) {
            for (String prefix : PROPERTY_NAME_PREFIXES) {
                if (name.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Copies the supported properties from the VO properties to the entity properties **/
    public static void copyFromVo(Map<String, String> entityProps, Map<String, Object> voProps) {
        voProps.entrySet().stream()
                // Only persist supported property names:
                .filter(e -> supportedProperty(e.getKey()))
                // Only persist non-blank values:
                .filter(e -> e.getValue() != null && !e.getValue().toString().isEmpty())
                // Persist the property as a string
                .forEach(e -> entityProps.put(e.getKey(), e.getValue().toString().trim()));
    }

    /** Copies the supported properties from the entity properties to the VO properties **/
    public static void copyToVo(Map<String, String> entityProps, Map<String, Object> voProps) {
        entityProps.entrySet().stream()
                .forEach(e -> {
                    if (NUMERIC_PROPERTIES.contains(e.getKey())) {
                        voProps.put(e.getKey(), Double.valueOf(e.getValue()));
                    } else {
                        voProps.put(e.getKey(), e.getValue());
                    }
                });
    }
}