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
package org.niord.core.category;

import org.apache.commons.lang.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.aton.AtonFilter;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.domain.Domain;
import org.niord.core.domain.DomainService;
import org.niord.core.script.ScriptResource;
import org.niord.core.service.TreeBaseService;
import org.niord.model.search.PagedSearchParamsVo;
import org.slf4j.Logger;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business interface for accessing Niord categories
 */
@Stateless
@SuppressWarnings("unused")
public class CategoryService extends TreeBaseService<Category> {

    public static final String SETTING_CATEGORY_LAST_UPDATED = "categoryLastUpdate";

    @Inject
    private Logger log;

    @Inject
    DomainService domainService;

    @Inject
    NiordApp app;


    /***************************************/
    /** Category look-up                  **/
    /***************************************/


    /**
     * Returns the category with the given legacy id
     *
     * @param legacyId the id of the category
     * @return the category with the given id or null if not found
     */
    public Category findByLegacyId(String legacyId) {
        try {
            return em.createNamedQuery("Category.findByLegacyId", Category.class)
                    .setParameter("legacyId", legacyId)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Searches for categories matching the given term in the given language
     *
     * @param params the sesarch params
     * @return the search result
     */
    @SuppressWarnings("all")
    public List<Category> searchCategories(CategorySearchParams params) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Category> categoryQuery = cb.createQuery(Category.class);

        Root<Category> categoryRoot = categoryQuery.from(Category.class);

        // Build the predicate
        CriteriaHelper<Category> criteriaHelper = new CriteriaHelper<>(cb, categoryQuery);

        // Match the name
        Join<Category, CategoryDesc> descs = categoryRoot.join("descs", JoinType.LEFT);
        if (params.isExact()) {
            criteriaHelper.equalsIgnoreCase(descs.get("name"), params.getName());
        } else  {
            criteriaHelper.like(descs.get("name"), params.getName());
        }

        // Optionally, filter by type
        criteriaHelper.equals(categoryRoot.get("type"), params.getType());

        // Optionally, match the language
        if (StringUtils.isNotBlank(params.getLanguage())) {
            criteriaHelper.equals(descs.get("lang"), params.getLanguage());
        }

        // Optionally, match the parent
        if (params.getParentId() != null) {
            categoryRoot.join("parent", JoinType.LEFT);
            Path<Category> parent = categoryRoot.get("parent");
            criteriaHelper.equals(parent.get("id"), params.getParentId());
        }

        // Optionally, match any ancestor
        if (params.getAncestorId() != null) {
            Category ancestor = getCategoryDetails(params.getAncestorId());
            if (ancestor != null) {
                criteriaHelper.add(cb.like(categoryRoot.get("lineage"), ancestor.getLineage() + "%/"));
            }
        }

        // Optionally, filter by domain
        if (StringUtils.isNotBlank(params.getDomain())) {

            Domain d = domainService.findByDomainId(params.getDomain());

            // First, filter by categories associated with the current domain
            if (d != null && d.getCategories().size() > 0) {
                Predicate[] categoryMatch = d.getCategories().stream()
                        .map(c -> cb.like(categoryRoot.get("lineage"), c.getLineage() + "%"))
                        .toArray(Predicate[]::new);
                criteriaHelper.add(cb.or(categoryMatch));
            }

            // Next, filter template messages by their associated list of domains
            if (d != null) {
                Join<Category, Domain> domains = categoryRoot.join("domains", JoinType.LEFT);
                criteriaHelper.add(cb.or(
                        cb.isEmpty(categoryRoot.get("domains")), // Either no domains specified
                        cb.equal(domains.get("domainId"), d.getDomainId()) // or domain in domain list
                ));
            }
        }

        // Unless the "inactive" search flag is set, only include active categories.
        if (!params.isInactive()) {
            criteriaHelper.add(cb.equal(categoryRoot.get("active"), true));
        }

        // Compute the sort order
        List<Order> sortOrders = new ArrayList<>();
        if (CategorySearchParams.TREE_SORT_ORDER.equals(params.getSortBy())) {
            Arrays.asList("treeSortOrder", "siblingSortOrder", "id")
                    .forEach(field -> {
                        if (params.getSortOrder() == PagedSearchParamsVo.SortOrder.ASC) {
                            sortOrders.add(cb.asc(categoryRoot.get(field)));
                        } else {
                            sortOrders.add(cb.desc(categoryRoot.get(field)));
                        }
                    });
        }

        // Complete the query
        categoryQuery.select(categoryRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(sortOrders);

        // Execute the query and update the search result
        List<Category> categories = em.createQuery(categoryQuery)
                .getResultList();

        // Optionally, filter on AtoNs
        // NB: Expensive!
        if (params.getAtons() != null && !params.getAtons().isEmpty()) {
            categories = resolveAtonCategories(categories, params.getAtons(), params.getMaxSize());
        }

        return categories.stream()
                .limit(params.getMaxSize())
                .collect(Collectors.toList());
    }


    /**
     * Returns the hierarchical list of root categories.
     * <p>
     * @return the hierarchical list of root categories
     */
    public List<Category> getCategoryTree() {
        return getTree(Category.class, "Category.findCategoriesWithDescs");
    }


    /** {@inheritDoc} **/
    @Override
    public List<Category> getRootEntities() {
        return em.createNamedQuery("Category.findRootCategories", Category.class)
                .getResultList();
    }


    /**
     * Looks up an category
     *
     * @param id the id of the category
     * @return the category
     */
    public Category getCategoryDetails(Integer id) {
        return getByPrimaryKey(Category.class, id);
    }


    /**
     * Looks up the categories with the given IDs
     *
     * @param ids the ids of the category
     * @return the category
     */
    public List<Category> getCategoryDetails(List<Integer> ids) {
        return em.createNamedQuery("Category.findCategoriesWithIds", Category.class)
                .setParameter("ids", ids)
                .getResultList().stream()
                .sorted(Comparator.comparingInt(c -> ids.indexOf(c.getId())))
                .collect(Collectors.toList());
    }


    /***************************************/
    /** Category life-cycles              **/
    /***************************************/


    /**
     * Updates the category data from the category template, but not the parent-child hierarchy of the category
     *
     * @param category the category to update
     * @return the updated category
     */
    public Category updateCategoryData(Category category) {
        Category original = getByPrimaryKey(Category.class, category.getId());
        return updateCategoryData(original, category);
    }


    /**
     * Updates the category data from the category template, but not the parent-child hierarchy of the category
     *
     * @param original the original category to update
     * @param category the template category to update the original with
     * @return the updated category
     */
    public Category updateCategoryData(Category original, Category category) {

        original.setType(category.getType());
        original.setMrn(category.getMrn());
        original.setActive(category.isActive());
        original.setSiblingSortOrder(category.getSiblingSortOrder());
        original.copyDescsAndRemoveBlanks(category.getDescs());
        original.getEditorFields().clear();
        original.getEditorFields().addAll(category.getEditorFields());
        original.setAtonFilter(category.getAtonFilter());

        original.updateLineage();
        original.updateActiveFlag();

        original.getScriptResourcePaths().clear();
        category.getScriptResourcePaths().stream()
                .filter(p -> ScriptResource.path2type(p) != null)
                .forEach(p -> original.getScriptResourcePaths().add(p));
        original.setMessageId(category.getMessageId());

        // Replace domains with persisted entities
        original.setDomains(domainService.persistedDomains(category.getDomains()));

        original.getStdTemplateFields().clear();
        original.getStdTemplateFields().addAll(category.getStdTemplateFields());

        original.getTemplateParams().clear();
        original.getTemplateParams().addAll(category.getTemplateParams());

        return saveEntity(original);
    }


    /**
     * If data has changed, updates the category data from the category template,
     * but not the parent-child hierarchy of the category
     *
     * @param original the original category to update
     * @param category the template category to update the original with
     * @return the updated category
     */
    public Category checkUpdateCategoryData(Category original, Category category) {
        if (original.hasChanged(category)) {
            return updateCategoryData(original, category);
        }
        return original;
    }


    /**
     * Creates a new category based on the category template
     * @param category the category to create
     * @param parentId the id of the parent category
     * @return the created category
     */
    public Category createCategory(Category category, Integer parentId) {

        if (parentId != null) {
            Category parent = getByPrimaryKey(Category.class, parentId);
            parent.addChild(category);
        }

        // Replace domains with persisted entities
        category.setDomains(domainService.persistedDomains(category.getDomains()));

        category = saveEntity(category);

        // The category now has an ID - Update lineage
        category.updateLineage();
        category.updateActiveFlag();
        category = saveEntity(category);

        em.flush();
        return category;
    }


    /**
     * Moves the category to the given parent id
     * @param categoryId the id of the category to create
     * @param parentId the id of the parent category
     * @return if the category was moved
     */
    public boolean moveCategory(Integer categoryId, Integer parentId) {
        return moveEntity(Category.class, categoryId, parentId);
    }


    /**
     * Changes the sort order of an category, by moving it up or down compared to siblings.
     * <p>
     * Please note that by moving "up" we mean in a geographical tree structure,
     * i.e. a smaller sortOrder value.
     *
     * @param categoryId the id of the category to move
     * @param moveUp whether to move the category up or down
     * @return if the category was moved
     */
    public boolean changeSortOrder(Integer categoryId, boolean moveUp) {
        return changeSortOrder(Category.class, categoryId, moveUp);
    }


    /**
     * Deletes the category and sub-categories
     * @param categoryId the id of the category to delete
     */
    public boolean deleteCategory(Integer categoryId) {

        Category category = getByPrimaryKey(Category.class, categoryId);
        if (category != null) {
            // Remove parent category relation
            category.setParent(null);
            saveEntity(category);
            remove(category);
            log.debug("Removed category " + categoryId);
            return true;
        }
        return false;
    }


    /**
     * Looks up an category by name
     * @param name the name to search for
     * @param lang the language. Optional
     * @param parentId the parent ID. Optional
     * @return The matching category, or null if not found
     */
    public Category findByName(String name, String lang, Integer parentId) {
        if (StringUtils.isBlank(name)) {
            return null;
        }

        CategorySearchParams params = new CategorySearchParams();
        params.parentId(parentId)
                .language(lang)
                .inactive(true) // Also search inactive categorys
                .name(name)
                .exact(true) // Not substring matches
                .maxSize(1);

        List<Category> categories = searchCategories(params);

        return categories.isEmpty() ? null : categories.get(0);
    }


    /**
     * Returns the category with the given MRN. Returns null if the category is not found.
     *
     * @param mrn the MRN of the category
     * @return the category with the given MRN or null if not found
     */
    public Category findByMrn(String mrn) {
        try {
            return em.createNamedQuery("Category.findByMrn", Category.class)
                    .setParameter("mrn", mrn)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns the category with the given ID or MRN. Returns null if the category is not found.
     *
     * @param categoryId the ID or MRN of the category
     * @return the category with the given ID or MRN or null if not found
     */
    public Category findByCategoryId(String categoryId) {
        if (StringUtils.isNumeric(categoryId)) {
            return getCategoryDetails(Integer.valueOf(categoryId));
        }
        return findByMrn(categoryId);
    }


    /**
     * Ensures that the template category and it's parents exists
     *
     * @param templateCategory the template category
     * @param create whether to create a missing category or just find it
     * @param update whether to update an existing category or just find it
     * @return the category
     */
    public Category importCategory(Category templateCategory, boolean create, boolean update) {
        // Sanity checks
        if (templateCategory == null) {
            return null;
        }

        // Check if we can find the category by MRN
        if (StringUtils.isNotBlank(templateCategory.getMrn())) {
            Category category = findByMrn(templateCategory.getMrn());
            if (category != null) {
                return update ? checkUpdateCategoryData(category, templateCategory) : category;
            }
        }

        // Recursively, resolve the parent categories
        Category parent = null;
        if (templateCategory.getParent() != null) {
            parent = importCategory(templateCategory.getParent(), create, update);
            if (!create && parent == null) {
                return null;
            }
        }
        Integer parentId = (parent == null) ? null : parent.getId();

        // Check if we can find the given category
        Category category = null;
        for (int x = 0; category == null && x < templateCategory.getDescs().size(); x++) {
            CategoryDesc desc = templateCategory.getDescs().get(x);
            category = findByName(desc.getName(), desc.getLang(), parentId);
        }

        // Create the category if no matching category was found
        if (create && category == null) {
            category = createCategory(templateCategory, parentId);
        } else if (update && category != null) {
            category = checkUpdateCategoryData(category, templateCategory);
        }
        return category;
    }


    /**
     * Returns the firing exercises category or null if none is found.
     *
     * The current approach naively searches for a category with the name "Firing Exercises".
     * TODO: A future approach should look up the category by a standardized MRN.
     *
     * @return the firing exercises category or null if none is found
     */
    public Category getFiringExercisesCategory() {
        return findByName("Firing Exercises", "en", null);
    }


    /**
     * Returns the last change date for categories or null if no category exists
     * @return the last change date for categories
     */
    @Override
    public Date getLastUpdated() {
        try {
            return em.createNamedQuery("Category.findLastUpdated", Date.class).getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Called periodically every hour to re-sort the category tree
     *
     * Potentially, a heavy-duty function that scans the entire category tree,
     * sorts it and update the treeSortOrder. Use with care.
     *
     * @return if the sort order was updated
     */
    @Schedule(persistent = false, second = "13", minute = "23", hour = "*")
    public boolean recomputeTreeSortOrder() {
        return recomputeTreeSortOrder(SETTING_CATEGORY_LAST_UPDATED);
    }


    /***************************************/
    /** AtonN functionality               **/
    /***************************************/


    /**
     * Resolves all categories that matches the given AtoNs
     * <p>
     * NB: This is a potentially expensive operation involving iteration of category lineages,
     *     including JavaScript evaluation of AtoN filters.
     *     However, given that there are only dozens - and not hundreds - of categories,
     *     we should be fine. Use with care though...
     *
     * @param categories the gross set of template categories to filter by AtoNs
     * @param atons the atons to find templates for
     * @param limit the max number of category templates to return
     * @return the matching template categories
     */
    private List<Category> resolveAtonCategories(List<Category> categories, List<AtonNodeVo> atons, int limit) {

        // Resolve matching categories via associated categories. Cache the result by category ID
        Map<Integer, Boolean> includeCategory = new HashMap<>();

        // Filter the templates
        return categories.stream()
                .filter(t -> matchesAtons(atons, t, includeCategory))
                .limit(limit)
                .collect(Collectors.toList());
    }


    /**
     * Checks if all AtoNs matches the AtoN filters of the template category lineage
     * @param atons the AtoNs to check
     * @param category the template category to test
     * @return if all AtoNs matches the AtoN filters of the template category lineage
     */
    private boolean matchesAtons(List<AtonNodeVo> atons, Category category, Map<Integer, Boolean> includeCategory) {

        // There must be at least one AtoN filter in the template category lineage to qualify
        boolean atonFiltered = false;

        // Check the category and all its parent categories
        for (Category cat = category; cat != null; cat = cat.getParent()) {
            if (includeCategory.containsKey(cat.getId())) {
                return includeCategory.get(cat.getId());
            }
            if (StringUtils.isNotBlank(cat.getAtonFilter())) {
                atonFiltered = true;
                boolean matchesAtons = matchesAtons(atons, cat.getAtonFilter());
                includeCategory.put(cat.getId(), matchesAtons);
                if (!matchesAtons) {
                    return false;
                }
            }
        }

        return atonFiltered;
    }


    /**
     * Checks if all AtoNs matches the AtoN filter
     * @param atons the AtoNs to check
     * @param atonFilter the AtoN filter to test
     * @return if all AtoNs matches the AtoN filter
     */
    private boolean matchesAtons(List<AtonNodeVo> atons, String atonFilter) {
        try {
            AtonFilter filter = AtonFilter.getInstance(atonFilter);
            return filter.matches(atons);
        } catch (ScriptException e) {
            return false;
        }
    }
}
