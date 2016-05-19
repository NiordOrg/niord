package org.niord.core.geojson;

import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Feature service
 */
@Stateless
public class FeatureService extends BaseService {


    @Inject
    private Logger log;

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
                .filter(f -> oldUids.containsKey(f.getProperties().getProperty("parentFeatureId")))
                .forEach(f -> {
                    String oldUid = f.getProperties().getProperty("parentFeatureId");
                    String newUid = oldUids.get(oldUid);
                    f.getProperties().setProperty("parentFeatureId", newUid);
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
