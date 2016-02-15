package org.niord.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.geom.Geometry;
import org.niord.core.conf.TextResource;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.niord.model.vo.geojson.FeatureVo;
import org.niord.core.model.Feature;
import org.niord.core.model.FeatureCollection;
import org.niord.core.util.GeoJsonUtils;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Feature service
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class FeatureService extends BaseService {


    @Inject
    private Logger log;

    @Inject
    @TextResource("/dk.json")
    String dkJson;

    @Inject
    @TextResource("/no.json")
    String noJson;

    /**
     * Persists the test data
     */
    @PostConstruct
    @Lock(LockType.WRITE)
    void init() {

        int cnt = em.createQuery("select f from Feature f").getResultList().size();
        if (cnt == 0) {
            Arrays.asList("/dk.json", "/no.json").forEach(json -> {
                try {
                    ObjectMapper mapper = new ObjectMapper();

                    FeatureCollectionVo featureCollection = mapper.readValue(
                            getClass().getResource(json),
                            FeatureCollectionVo.class);
                    FeatureVo feature = featureCollection.getFeatures()[0];

                    // Round to four decimals
                    GeoJsonUtils.roundCoordinates(feature, 4);

                    // Bizarrely, MarineRegions has coordinates in YX order instead of XY order
                    GeoJsonUtils.swapCoordinates(feature);

                    Geometry geometry = GeoJsonUtils.toJts(feature.getGeometry());

                    Feature featureEntity = new Feature();
                    featureEntity.setGeometry(geometry);

                    FeatureCollection featureCollectionEntity = new FeatureCollection();
                    featureCollectionEntity.getFeatures().add(featureEntity);
                    featureEntity.setFeatureCollection(featureCollectionEntity);

                    em.persist(featureCollectionEntity);
                    log.info("****  Persisted feature collection");

                } catch (Exception e) {
                    log.error("Error saving test shape ", e);
                }
            });
        }
    }

    /**
    public List<Feature> findMatchingShapes(double lon, double lat) throws Exception {
        WKTReader wktReader = new WKTReader();
        Geometry shape = wktReader.read("POINT (" + lon + " " + lat + ")");
        shape.setSRID(Feature.WGS84_SRID);

        return em.createNamedQuery("Feature.hits", Feature.class)
                .setParameter("pt", shape)
                .getResultList();
    }
     **/

    public Feature findFeatureByUid(String uid) {
        return em.createNamedQuery("Feature.findByUid", Feature.class)
                .setParameter("uid", uid)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    public FeatureCollection findFeatureCollectionByUid(String uid) {
        return em.createNamedQuery("FeatureCollection.findByUid", FeatureCollection.class)
                .setParameter("uid", uid)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<FeatureCollection> loadAllFeatureCollections() throws Exception {
        return em.createQuery("select fc from FeatureCollection fc", FeatureCollection.class)
                .getResultList();
    }


    /**
     * Assigns new UIDs to all features in a feature collection (e.g. for when a feature collection is copied).
     * Make sure that features with a "parentFeatureId" property gets updated to reference the new UID.
     * @param fc the feature collection to update.
     */
    private void assignNewFeatureUids(FeatureCollection fc) {

        // Assign new UIDs to the features, and record the old UIDs
        Map<String, String> oldUids = new HashMap<>();
        fc.getFeatures().forEach(f -> {
            String oldUid = f.getUid();
            String newUid = f.assignNewUid();
            if (oldUid != null) {
                oldUids.put(oldUid, newUid);
            }
        });

        // If there are any links between features, via the "parentFeatureId" property, update it to the new UID
        fc.getFeatures().stream()
                .filter(f -> f.getProperties().containsKey("parentFeatureId"))
                .filter(f -> oldUids.containsKey(f.getProperties().get("parentFeatureId")))
                .forEach(f -> {
                    String oldUid = f.getProperties().get("parentFeatureId");
                    String newUid = oldUids.get(oldUid);
                    f.getProperties().put("parentFeatureId", newUid);
                });
    }

    public FeatureCollection createFeatureCollection(FeatureCollection fc) throws Exception {
        requireNonNull(fc);
        fc.setUid(null);
        assignNewFeatureUids(fc);
        em.persist(fc);
        return fc;
    }

    public FeatureCollection updateFeatureCollection(FeatureCollection fc) throws Exception {
        requireNonNull(fc);
        requireNonNull(fc.getUid());
        FeatureCollection orig = requireNonNull(findFeatureCollectionByUid(fc.getUid()));

        orig.getFeatures().clear();
        fc.getFeatures().forEach(f -> {
            Feature of = findFeatureByUid(f.getUid());
            if (of != null) {
                of.setGeometry(f.getGeometry());
                // TODO: Properties
                orig.getFeatures().add(of);
            } else {
                f.setFeatureCollection(orig);
                orig.getFeatures().add(f);
            }
        });

        em.persist(orig);
        return orig;
    }
}
