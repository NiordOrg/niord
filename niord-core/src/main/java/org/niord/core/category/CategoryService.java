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
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business interface for accessing Niord categories
 */
@Stateless
@SuppressWarnings("unused")
public class CategoryService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    DomainService domainService;

    @Inject
    NiordApp app;

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

        // Complete the query
        categoryQuery.select(categoryRoot)
                .distinct(true)
                .where(criteriaHelper.where());
                //.orderBy(cb.asc(cb.locate(cb.lower(descs.get("name")), name.toLowerCase())));

        // Execute the query and update the search result
        List<Category> categories = em.createQuery(categoryQuery)
                .getResultList();

        // Optionally, filter on AtoNs
        // NB: Expensive!
        if (params.getAtons() != null && !params.getAtons().isEmpty()) {
            categories = resolveAtonCategories(categories, params.getAtons());
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

        // Get all categories along with their CategoryDesc records
        // Will ensure that all Category entities are cached in the entity manager before organizing the result
        List<Category> categories = em
                .createNamedQuery("Category.findCategoriesWithDescs", Category.class)
                .getResultList();

        // Extract the roots
        return categories.stream()
                .filter(Category::isRootCategory)
                // TODO .sorted()
                .collect(Collectors.toList());
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
    public List<Category> getCategoryDetails(Set<Integer> ids) {
        return em.createNamedQuery("Category.findCategoriesWithIds", Category.class)
                .setParameter("ids", ids)
                .getResultList();
    }


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
        Category category = getByPrimaryKey(Category.class, categoryId);

        if (category.getParent() != null && category.getParent().getId().equals(parentId)) {
            return false;
        }

        if (category.getParent() != null) {
            category.getParent().getChildren().remove(category);
        }

        if (parentId == null) {
            category.setParent(null);
        } else {
            Category parent = getByPrimaryKey(Category.class, parentId);
            parent.addChild(category);
        }

        // Save the entity
        saveEntity(category);
        em.flush();

        // Update all lineages
        updateLineages();
        category.updateActiveFlag();

        return true;
    }


    /**
     * Update lineages for all categories
     */
    public void updateLineages() {

        log.info("Update category lineages");

        // Get root categories
        List<Category> roots = getAll(Category.class).stream()
            .filter(Category::isRootCategory)
            .collect(Collectors.toList());

        // Update each root subtree
        List<Category> updated = new ArrayList<>();
        roots.forEach(category -> updateLineages(category, updated));

        // Persist the changes
        updated.forEach(this::saveEntity);
        em.flush();
    }


    /**
     * Recursively updates the lineages of categories rooted at the given category
     * @param category the category whose sub-tree should be updated
     * @param categories the list of updated categories
     * @return if the lineage was updated
     */
    private boolean updateLineages(Category category, List<Category> categories) {

        boolean updated = category.updateLineage();
        if (updated) {
            categories.add(category);
        }
        category.getChildren().forEach(childCategory -> updateLineages(childCategory, categories));
        return updated;
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
                .inactive(true) // Also search inactive areas
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

        // Check if we can find the area by MRN
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
     * @param atons the atons to find templates for
     * @return the matching template categories
     */
    private List<Category> resolveAtonCategories(List<Category> categories, List<AtonNodeVo> atons) {

        // Resolve matching categories via associated categories. Cache the result by category ID
        Map<Integer, Boolean> includeCategory = new HashMap<>();

        // Filter the templates
        return categories.stream()
                .filter(t -> matchesAtons(atons, t, includeCategory))
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
