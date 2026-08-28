/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.web.publication;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.niord.core.user.Roles;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.niord.core.publication.PublicationCategory;
import org.niord.core.publication.PublicationCategoryDesc;
import org.niord.core.publication.PublicationCategoryService;
import org.niord.core.publication.series.IssueLifecycleService;
import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.slf4j.Logger;

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
    Logger log;

    @Inject
    PublicationCategoryService categoryService;

    /** The bound an anonymous read falls back to when none is given. */
    public static final int DEFAULT_LIMIT = 1000;

    /** C1. Everything, for a picker. */
    @GET
    @Path("/all")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    @NoCache
    @PermitAll
    public List<Map<String, Object>> all(@QueryParam("lang") String lang,
                                         @QueryParam("limit") @DefaultValue("1000") int limit) {
        // The limit is a real bound, not a page, and it is ALWAYS applied. This
        // endpoint is anonymous and externally consumed -- the admin "Export…"
        // link is a bare href with no role and no ticket -- so an absent or zero
        // limit meaning "everything" hands anybody an unbounded read of the
        // table. The default is the one the shipped contract carries; the
        // parameter name is that contract too, and the asymmetry with
        // /publications/all's maxSize is deliberate and must not be "fixed".
        List<Map<String, Object>> out = new ArrayList<>();
        for (PublicationCategory c : categoryService.listByPriority(limit > 0 ? limit : DEFAULT_LIMIT)) {
            out.add(toMap(c, lang));
        }
        return out;
    }

    /** C3. One category. */
    @GET
    @Path("/publication-category/{categoryId}")
    @Produces(MediaType.APPLICATION_JSON)
    @GZIP
    @NoCache
    @PermitAll
    public Map<String, Object> get(@PathParam("categoryId") String categoryId,
                                   @QueryParam("lang") String lang) {
        return toMap(categoryService.requireByCategoryId(categoryId), lang);
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
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> create(Map<String, Object> body) {
        // "" and not null, so the emptiness test below answers an absent body
        // with the same coded refusal it answers an absent field -- rather than
        // with a NullPointerException the caller reads as a server failure.
        String categoryId = body == null ? "" : String.valueOf(body.getOrDefault("categoryId", "")).trim();
        if (categoryId.isEmpty()) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_INVALID",
                    "categoryId is required; it is the stable key a series stores");
        }

        PublicationCategory c = new PublicationCategory();
        c.setCategoryId(categoryId);
        // Lower sorts first on the public page, and a new category lands at the
        // back rather than silently ahead of everything that was there.
        c.setPriority(body.containsKey("priority") && body.get("priority") instanceof Number n
                ? n.intValue() : DEFAULT_PRIORITY);
        c.setPublish(Boolean.TRUE.equals(body.get("publish")));
        applyDescs(c, body);
        PublicationCategory saved = categoryService.createUnderNewId(c);
        log.info("Created publication category {}", categoryId);
        return toMap(saved, null);
    }

    /** Where a category lands in the public page's order when nobody says. */
    public static final int DEFAULT_PRIORITY = 100;

    /** C5. Update. */
    @PUT
    @Path("/publication-category/{categoryId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMIN)
    public Map<String, Object> update(@PathParam("categoryId") String categoryId,
                                      Map<String, Object> body) {
        if (body == null) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_INVALID",
                    "a category body is required");
        }
        // The path wins, and a body that disagrees is refused rather than
        // silently ignored. categoryId is what a series stores and what an
        // import upserts on, so a caller that believes it renamed a category and
        // did not is worse off than one that got an error.
        Object bodyId = body.get("categoryId");
        if (bodyId != null && !String.valueOf(bodyId).isBlank()
                && !categoryId.equals(String.valueOf(bodyId))) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_ID_IMMUTABLE",
                    "categoryId is the key every series stores; it cannot be changed after create. "
                            + "The path says '" + categoryId + "' and the body says '" + bodyId + "'");
        }

        PublicationCategory c = categoryService.requireByCategoryId(categoryId);
        // The same instanceof guard create uses two methods up. A blind cast made
        // {"priority": null} -- and {"priority": "100"} -- a 500 out of an
        // ordinary form save.
        if (body.get("priority") instanceof Number n) {
            c.setPriority(n.intValue());
        } else if (body.containsKey("priority") && body.get("priority") != null) {
            throw new IssueLifecycleService.TransitionRefusedException("CATEGORY_INVALID",
                    "priority is a number; it decides where the category sorts on the public page");
        }
        if (body.containsKey("publish")) {
            c.setPublish(Boolean.TRUE.equals(body.get("publish")));
        }
        // The NAMES, which this could not previously change -- so a category could be
        // created with a typo and never corrected, and the public page carries these.
        applyDescs(c, body);
        Map<String, Object> out = toMap(categoryService.save(c), null);
        log.info("Updated publication category {}", categoryId);
        return out;
    }

    /** C6. Delete, refused while anything still points at it. */
    @DELETE
    @Path("/publication-category/{categoryId}")
    @RolesAllowed(Roles.ADMIN)
    public void delete(@PathParam("categoryId") String categoryId) {
        categoryService.deleteUnreferenced(categoryId);
        log.info("Deleted publication category {}", categoryId);
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
    @RolesAllowed(Roles.ADMIN)
    public String importCategories(MultipartFormDataInput input) throws Exception {
        return executeBatchJobFromUploadedFile(input, "publication-category-import");
    }

    /**
     * The wire shape of one category.
     *
     * Package-visible and static because the language fallback below is a rule
     * rather than a formatting detail, and testing it through the endpoint means
     * standing up a container this module does not have.
     */
    static Map<String, Object> toMap(PublicationCategory c, String lang) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categoryId", c.getCategoryId());
        out.put("priority", c.getPriority());
        out.put("publish", c.isPublish());
        // The requested language, or the first one the category HAS.
        //
        // The fallback is not a nicety. A category with no desc in the requested
        // language used to answer with an empty descs array, and every consumer
        // of that -- the series form's dropdown, the still-shipped admin list --
        // then rendered the raw slug or threw on descs[0].name. Every other
        // localized read in the product falls back the same way, and this is the
        // shape that was there before the two category resources were merged.
        List<PublicationCategoryDesc> rows = c.getDescs().stream()
                .filter(d -> lang == null || lang.equals(d.getLang()))
                .toList();
        if (rows.isEmpty() && !c.getDescs().isEmpty()) {
            rows = List.of(c.getDescs().get(0));
        }

        List<Map<String, String>> descs = new ArrayList<>();
        for (PublicationCategoryDesc d : rows) {
            Map<String, String> dm = new LinkedHashMap<>();
            dm.put("lang", d.getLang());
            dm.put("name", d.getName());
            // The description exists on the entity and is carried here, so that
            // keeping it is a decision rather than an accident of which fields
            // somebody happened to map.
            dm.put("description", d.getDescription());
            descs.add(dm);
        }
        out.put("descs", descs);
        return out;
    }
}
