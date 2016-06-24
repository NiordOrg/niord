package org.niord.core.geojson;

import org.niord.core.service.BaseService;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Make sure that features with a "parentFeatureIds" property gets updated to reference the new UID.
     * @param fc the feature collection to update.
     */
    private void assignNewFeatureUids(FeatureCollection fc) {

        // Assign new UIDs to the features, and record the old UIDs
        Map<Object, String> oldUids = new HashMap<>();
        fc.getFeatures().forEach(f -> {
            String oldUid = f.getUid();
            String newUid = f.assignNewUid();
            if (oldUid != null) {
                oldUids.put(oldUid, newUid);
            }
        });

        // If there are any links between features, via the "parentFeatureIds" comma-separated feature id property,
        // update it to the new UID
        fc.getFeatures().stream()
                .filter(f -> f.getProperties().containsKey("parentFeatureIds"))
                .forEach(f -> {
                    String parentFeatureIds = (String)f.getProperties().get("parentFeatureIds");
                    String newParentFeatureIds = Arrays.stream(parentFeatureIds.split(","))
                            .map(id -> oldUids.containsKey(id) ? oldUids.get(id) : id)
                            .filter(id -> id != null)
                            .collect(Collectors.joining(","));
                    f.getProperties().put("parentFeatureIds", newParentFeatureIds);
                });
    }


    /**
     * Copies a feature collection and assign new UID's to all features and the feature collection
     * @param fc the feature collection to copy
     * @return the copy
     */
    public FeatureCollectionVo copyFeatureCollection(FeatureCollectionVo fc) {
        if (fc == null) {
            return null;
        }
        FeatureCollection featureCollection = FeatureCollection.fromGeoJson(fc);
        featureCollection.assignNewUid();
        assignNewFeatureUids(featureCollection);
        return featureCollection.toGeoJson();
    }


    /**
     * Create and persist a new feature collection from the given template
     * @param fc the feature collection template
     * @return the persisted feature collection
     */
    public FeatureCollection createFeatureCollection(FeatureCollection fc) throws Exception {
        requireNonNull(fc);
        fc.setUid(null);
        assignNewFeatureUids(fc);
        em.persist(fc);
        return fc;
    }


    /**
     * Updates the given feature collection
     * @param fc the feature collection
     * @return the updated feature collection
     */
    public FeatureCollection updateFeatureCollection(FeatureCollection fc) throws Exception {
        if (fc == null) {
            return null;
        }
        FeatureCollection orig = requireNonNull(findFeatureCollectionByUid(fc.getUid()));

        orig.getFeatures().clear();
        fc.getFeatures().forEach(f -> {
            Feature of = findFeatureByUid(f.getUid());
            if (of != null) {
                of.setGeometry(f.getGeometry());
                of.getProperties().clear();
                of.getProperties().putAll(f.getProperties());
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
