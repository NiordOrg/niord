package org.niord.web.publication;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryDesc;
import org.niord.core.publication.series.IssueLifecycleService;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publication categories.
 *
 * The existing entity, unchanged apart from the priority default. Categories are
 * shared with the legacy publication model, so this resource reads and writes
 * the same rows the old one does -- which is why it does not get its own VO
 * hierarchy or its own table.
 */
@Path("/publication-categories")
@RequestScoped
@Transactional
@SuppressWarnings("unused")
public class PublicationCategoryRestService extends org.niord.core.batch.AbstractBatchableRestService {

    @Inject
    EntityManager em;

    /** C1. Everything, for a picker. */
    @GET
    @Path("/all")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public List<Map<String, Object>> all(@QueryParam("lang") String lang,
                                         @QueryParam("limit") Integer limit) {
        var query = em.createQuery(
                "SELECT c FROM PublicationCategory c ORDER BY c.priority ASC, c.categoryId ASC",
                PublicationCategory.class);
        // The limit is a real bound, not a page: every shipped picker in the
        // legacy app capped silently at 100 and nobody noticed until a list grew.
        if (limit != null && limit > 0) {
            query.setMaxResults(limit);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (PublicationCategory c : query.getResultList()) {
            out.add(toMap(c, lang));
        }
        return out;
    }

    /** C3. One category. */
    @GET
    @Path("/publication-category/{categoryId}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Map<String, Object> get(@PathParam("categoryId") String categoryId,
                                   @QueryParam("lang") String lang) {
        return toMap(required(categoryId), lang);
    }

    /**
     * The per-language names, merged in place.
     *
     * In place, not cleared and rebuilt: the desc table is unique on (lang, entity)
     * and Hibernate orders inserts before deletes within a flush, so rebuilding a
     * row for a language that already has one collides with itself. The series
     * model was found failing exactly that way on every edit after the first.
     */
    @SuppressWarnings("unchecked")
    static void applyDescs(PublicationCategory c, Map<String, Object> body) {
        Object raw = body.get("descs");
        if (!(raw instanceof List<?> incoming)) {
            return;
        }
        List<String> sent = new ArrayList<>();
        for (Object o : incoming) {
            if (o instanceof Map<?, ?> m && m.get("lang") != null) {
                sent.add(m.get("lang").toString());
            }
        }
        c.getDescs().removeIf(d -> !sent.contains(d.getLang()));

        for (Object o : incoming) {
            if (!(o instanceof Map<?, ?> m) || m.get("lang") == null) {
                continue;
            }
            String lang = m.get("lang").toString();
            PublicationCategoryDesc d = c.getDescs().stream()
                    .filter(x -> lang.equals(x.getLang()))
                    .findFirst()
                    .orElseGet(() -> c.createDesc(lang));
            d.setName(m.get("name") == null ? null : m.get("name").toString());
            d.setDescription(m.get("description") == null ? null : m.get("description").toString());
        }
    }

    /**
     * C2. Create.
     *
     * A series cannot be saved without a category, so without this the only way
     * to get one was the batch import -- and a deployment that needed a new
     * section on the public page had to be given one by a sysadmin with a JSON
     * file.
     */
    @POST
    @Path("/publication-category/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> create(Map<String, Object> body) {
        String categoryId = body == null ? null : String.valueOf(body.getOrDefault("categoryId", "")).trim();
        if (categoryId.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_INVALID",
                    "categoryId is required; it is the stable key a series stores");
        }
        boolean taken = !em.createQuery(
                        "SELECT c FROM PublicationCategory c WHERE c.categoryId = :id",
                        PublicationCategory.class)
                .setParameter("id", categoryId).getResultList().isEmpty();
        if (taken) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_ID_TAKEN",
                    "a category with id '" + categoryId + "' already exists");
        }

        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(categoryId);
        // Lower sorts first on the public page, and a new category lands at the
        // back rather than silently ahead of everything that was there.
        c.setPriority(body.containsKey("priority") && body.get("priority") instanceof Number n
                ? n.intValue() : DEFAULT_PRIORITY);
        c.setPublish(Boolean.TRUE.equals(body.get("publish")));
        applyDescs(c, body);
        em.persist(c);
        em.flush();
        return toMap(c, null);
    }

    /** Where a category lands in the public page's order when nobody says. */
    public static final int DEFAULT_PRIORITY = 100;

    /** C5. Update. */
    @PUT
    @Path("/publication-category/{categoryId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    public Map<String, Object> update(@PathParam("categoryId") String categoryId,
                                      Map<String, Object> body) {
        PublicationCategory c = required(categoryId);
        if (body.containsKey("priority")) {
            c.setPriority(((Number) body.get("priority")).intValue());
        }
        if (body.containsKey("publish")) {
            c.setPublish(Boolean.TRUE.equals(body.get("publish")));
        }
        // The NAMES, which this could not previously change -- so a category could be
        // created with a typo and never corrected, and the public page carries these.
        applyDescs(c, body);
        return toMap(em.merge(c), null);
    }

    /** C6. Delete, refused while anything still points at it. */
    @DELETE
    @Path("/publication-category/{categoryId}")
    @RolesAllowed("admin")
    public void delete(@PathParam("categoryId") String categoryId) {
        PublicationCategory c = required(categoryId);

        // BOTH models, because both still store this row. Counting only the new
        // side would let a category be deleted out from under the publications
        // the legacy list is still serving, and the failure would surface as a
        // missing section on the public page rather than as a refusal here.
        Long series = em.createQuery(
                        "SELECT COUNT(s) FROM PublicationSeries s WHERE s.category = :c", Long.class)
                .setParameter("c", c).getSingleResult();
        Long publications = em.createQuery(
                        "SELECT COUNT(p) FROM Publication p WHERE p.category = :c", Long.class)
                .setParameter("c", c).getSingleResult();
        if (series + publications > 0) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_IN_USE",
                    series + " series and " + publications + " publication(s) still belong to '"
                            + categoryId + "'. Move them to another category first.");
        }
        em.remove(c);
    }

    /**
     * C7. Seed categories from an uploaded JSON file.
     *
     * The sysadmin bootstrap path, carried over unchanged. It is how a fresh
     * deployment gets the sections the public page is built from, and there is no
     * REST equivalent worth building: this is a file somebody produced from
     * another installation, not a form.
     */
    @POST
    @Path("/upload-publication-categories")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importCategories(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "publication-category-import");
    }

    private Map<String, Object> toMap(PublicationCategory c, String lang) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categoryId", c.getCategoryId());
        out.put("priority", c.getPriority());
        out.put("publish", c.isPublish());
        List<Map<String, String>> descs = new ArrayList<>();
        c.getDescs().forEach(d -> {
            if (lang == null || lang.equals(d.getLang())) {
                Map<String, String> dm = new LinkedHashMap<>();
                dm.put("lang", d.getLang());
                dm.put("name", d.getName());
                // CAT-004: the description exists on the entity and is carried
                // here, so that keeping it is a decision rather than an accident
                // of which fields somebody happened to map.
                dm.put("description", d.getDescription());
                descs.add(dm);
            }
        });
        out.put("descs", descs);
        return out;
    }

    private PublicationCategory required(String categoryId) {
        List<PublicationCategory> found = em.createQuery(
                        "SELECT c FROM PublicationCategory c WHERE c.categoryId = :id",
                        PublicationCategory.class)
                .setParameter("id", categoryId).getResultList();
        if (found.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_NOT_FOUND",
                    "no category with id " + categoryId);
        }
        return found.get(0);
    }
}
