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
package org.niord.web;

import org.jboss.resteasy.annotations.GZIP;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.security.annotation.SecurityDomain;
import org.niord.core.batch.AbstractBatchableRestService;
import org.niord.core.category.Category;
import org.niord.core.category.CategoryService;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.message.CategoryVo;
import org.slf4j.Logger;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST interface for accessing categories.
 */
@Path("/categories")
@Stateless
@SecurityDomain("keycloak")
@PermitAll
public class CategoryRestService extends AbstractBatchableRestService {

    @Inject
    Logger log;

    @Inject
    CategoryService categoryService;


    /**
     * Searches for categories matching the given name in the given language
     *
     * @param lang  the language
     * @param name  the search name
     * @param domain  restrict the search to the current domain or not
     * @param limit the maximum number of results
     * @return the search result
     */
    @GET
    @Path("/search")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<CategoryVo> searchCategories(
            @QueryParam("lang") String lang,
            @QueryParam("name") String name,
            @QueryParam("domain") @DefaultValue("false") boolean domain,
            @QueryParam("inactive") @DefaultValue("false") boolean inactive,
            @QueryParam("limit") int limit) {

        log.debug(String.format("Searching for categories lang=%s, name='%s', domain=%s, inactive=%s, limit=%d",
                lang, name, domain, inactive, limit));

        DataFilter filter = DataFilter.get()
                .fields(DataFilter.PARENT)
                .lang(lang);

        return categoryService.searchCategories(null, lang, name, domain, inactive, limit).stream()
                .map(c -> c.toVo(filter))
                .collect(Collectors.toList());
    }


    /** Returns the categories with the given IDs */
    @GET
    @Path("/search/{categoryIds}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<CategoryVo> searchCategoryIds(@PathParam("categoryIds") String categoryIds,
                                      @QueryParam("lang") @DefaultValue("en") String lang,
                                      @QueryParam("limit") @DefaultValue("1000") int limit) {

        log.debug(String.format("Searching for categories ids=%s, lang=%s, limit=%d", categoryIds, lang, limit));

        Set<Integer> ids = Arrays.stream(categoryIds.split(","))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.PARENT);

        return categoryService.getCategoryDetails(ids).stream()
                .map(c -> c.toVo(filter))
                .limit(limit)
                .collect(Collectors.toList());
    }


    /** Returns all categories via a list of hierarchical root categories */
    @GET
    @Path("/category-roots")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<CategoryVo> getCategoryRoots(@QueryParam("lang") String lang) {

        DataFilter filter = DataFilter.get()
                .lang(lang)
                .fields(DataFilter.CHILDREN);

        return categoryService.getCategoryTree().stream()
                .map(c -> c.toVo(filter))
                .collect(Collectors.toList());
    }


    /** Returns all categories via a list of hierarchical root categories */
    @GET
    @Path("/all")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public List<CategoryVo> getAll() {

        DataFilter filter = DataFilter.get()
                .fields(DataFilter.CHILDREN);

        return categoryService.getCategoryTree().stream()
                .map(c -> c.toVo(filter))
                .collect(Collectors.toList());
    }


    /** Returns the category with the given ID */
    @GET
    @Path("/category/{categoryId}")
    @Produces("application/json;charset=UTF-8")
    @GZIP
    @NoCache
    public CategoryVo getCategory(@PathParam("categoryId") Integer categoryId) throws Exception {
        log.debug("Getting category " + categoryId);
        Category category = categoryService.getCategoryDetails(categoryId);
        // Return the category without parent and child categories
        return category == null ? null : category.toVo(DataFilter.get());
    }

    /** Creates a new category */
    @POST
    @Path("/category/")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    public CategoryVo createCategory(CategoryVo categoryVo) throws Exception {
        Category category = new Category(categoryVo);
        log.info("Creating category " + category);
        Integer parentId = (categoryVo.getParent() == null) ? null : categoryVo.getParent().getId();
        return categoryService.createCategory(category, parentId).toVo(DataFilter.get());
    }


    /** Updates an category */
    @PUT
    @Path("/category/{categoryId}")
    @Consumes("application/json;charset=UTF-8")
    @Produces("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    public CategoryVo updateCategory(@PathParam("categoryId") Integer categoryId, CategoryVo categoryVo) throws Exception {
        if (!Objects.equals(categoryId, categoryVo.getId())) {
            throw new WebApplicationException(400);
        }
        Category category = new Category(categoryVo);
        log.info("Updating category " + category);
        return categoryService.updateCategoryData(category).toVo(DataFilter.get());
    }


    /** Deletes the given category */
    @DELETE
    @Path("/category/{categoryId}")
    @RolesAllowed({"admin"})
    public boolean deleteCategory(@PathParam("categoryId") Integer categoryId) throws Exception {
        log.info("Deleting category " + categoryId);
        return categoryService.deleteCategory(categoryId);
    }


    /** Move an category to a new parent category **/
    @PUT
    @Path("/move-category")
    @Consumes("application/json;charset=UTF-8")
    @RolesAllowed({"admin"})
    public boolean moveCategory(MoveCategoryVo moveCategoryVo) throws Exception {
        log.info("Moving category " + moveCategoryVo.getCategoryId() + " to " + moveCategoryVo.getParentId());
        return categoryService.moveCategory(moveCategoryVo.getCategoryId(), moveCategoryVo.getParentId());
    }


    /**
     * Imports an uploaded Categories json file
     *
     * @param request the servlet request
     * @return a status
     */
    @POST
    @Path("/upload-categories")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("text/plain")
    @RolesAllowed("admin")
    public String importCategories(@Context HttpServletRequest request) throws Exception {
        return executeBatchJobFromUploadedFile(request, "category-import");
    }


    /**
     * ******************
     * Helper classes
     * *******************
     */

    /** Encapsulates the parameters used for moving an category to a new parent category */
    @SuppressWarnings("unused")
    public static class MoveCategoryVo implements IJsonSerializable {
        Integer categoryId, parentId;

        public Integer getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Integer categoryId) {
            this.categoryId = categoryId;
        }

        public Integer getParentId() {
            return parentId;
        }

        public void setParentId(Integer parentId) {
            this.parentId = parentId;
        }
    }
}
