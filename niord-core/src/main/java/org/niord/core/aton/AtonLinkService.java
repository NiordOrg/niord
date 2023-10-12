/*
 * Copyright 2023 GLA UK Research and Development Directive
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
package org.niord.core.aton;

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.CriteriaHelper;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static org.niord.model.search.PagedSearchParamsVo.SortOrder.DESC;

/**
 * Business interface for managing AtoN links.
 *
 * AtoN links define aggregations and associations on AtoN as requested by the
 * IHO S-125 and IALA S-201 data products.
 */
@RequestScoped
@SuppressWarnings("unused")
public class AtonLinkService extends BaseService {

    @Inject
    Logger log;

    @Inject
    AtonService atonService;

    /**
     * Returns the AtoN link with the given ID
     *
     * @param linksId the AtoN link ID
     * @return the AtoN link with the given link identifier or null if not found
     */
    public AtonLink findAtonLink(UUID linksId) {
        List<AtonLink> links = findAtonLinks(linksId);
        return links.isEmpty() ? null : links.get(0);
    }

    /**
     * Returns the AtoN links with the given link IDs
     * @param linksIds the link IDs
     * @return the AtoN links with the given link identifiers
     */
    public List<AtonLink> findAtonLinks(UUID... linksIds) {
        if (linksIds == null || linksIds.length == 0) {
            return Collections.emptyList();
        }

        Set<UUID> idSet = new HashSet<>(Arrays.asList(linksIds));
        return em.createNamedQuery("AtonLink.findAtonLinksByLinkIds", AtonLink.class)
                .setParameter("linkIds", idSet)
                .getResultList()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }


    /**
     * Returns the AtoN links with the given type and names
     * @param type the link type
     * @param names the link names
     * @return the AtoN links with the given type and link names
     */
    public List<AtonLink> findAtonLinksByTypeAndName(AtonLinkType type, String... names) {
        if (type == null || names == null || names.length == 0) {
            return Collections.emptyList();
        }

        Set<String> nameSet = new HashSet<>(Arrays.asList(names));
        return em.createNamedQuery("AtonLink.findAtonLinksByTypeAndName", AtonLink.class)
                .setParameter("type", type)
                .setParameter("names", nameSet)
                .getResultList();
    }

    /**
     * Returns the AtoN links which contain the AtoN with the given UID
     * @param atonUid the AtoN UID
     * @return the AtoN links which contain the AtoN with the given UID
     */
    public List<AtonLink> findAtonLinksByAtonUid(String atonUid) {
        if (StringUtils.isBlank(atonUid)) {
            return Collections.emptyList();
        }

        return em.createNamedQuery("AtonLink.findAtonLinksByAtonTag", AtonLink.class)
                .setParameter("key", AtonTag.TAG_ATON_UID)
                .setParameter("values", new String[]{atonUid})
                .getResultList()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Searches for AtoN links matching the given search parameters
     *
     * @param params the search parameters
     * @return the search result
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public List<AtonLink> searchAtonLinks(AtonLinkSearchParams params) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<AtonLink> query = cb.createQuery(AtonLink.class);

        Root<AtonLink> linkRoot = query.from(AtonLink.class);

        // Build the predicate
        CriteriaHelper<AtonLink> criteriaHelper = new CriteriaHelper<>(cb, query);

        // Name filtering
        criteriaHelper.like(linkRoot.get("name"), params.getName());

        // Type filtering
        Set<AtonLinkType> types = params.getTypes() != null ? new HashSet<>(params.getTypes()) : new HashSet<>();
        if (types.isEmpty()) {
            types.add(AtonLinkType.AGGREGATION);
            types.add(AtonLinkType.ASSOCIATION);
        }
        List<Predicate> typePredicates = new LinkedList<>();
        if (types.contains(AtonLinkType.AGGREGATION)) {
            typePredicates.add(cb.equal(linkRoot.get("linkType"), AtonLinkType.AGGREGATION));
        }
        if (types.contains(AtonLinkType.ASSOCIATION)) {
            typePredicates.add(cb.equal(linkRoot.get("linkType"), AtonLinkType.ASSOCIATION));
        }
        criteriaHelper.add(cb.or(typePredicates.toArray(new Predicate[typePredicates.size()])));

        // Compute the sorting
        List<Order> sortOrders = new ArrayList<>();
        Order nameAscSortOrder = cb.asc(cb.lower(linkRoot.get("name")));
        if (params.sortByType()) {
            Expression sortBy = linkRoot.get("type");
            sortOrders.add(params.getSortOrder() == DESC ? cb.desc(sortBy) : cb.asc(sortBy));
            sortOrders.add(nameAscSortOrder);
        } else if (params.sortByCreated()) {
            Expression sortBy = linkRoot.get("created");
            sortOrders.add(params.getSortOrder() == DESC ? cb.desc(sortBy) : cb.asc(sortBy));
            sortOrders.add(nameAscSortOrder);
        } else if (params.sortByAtonCount()) {
            Expression sortBy = linkRoot.get("atonCount");
            sortOrders.add(params.getSortOrder() == DESC ? cb.desc(sortBy) : cb.asc(sortBy));
            sortOrders.add(nameAscSortOrder);
        } else {
            if (StringUtils.isNotBlank(params.getName())) {
                sortOrders.add(cb.asc(cb.locate(cb.lower(linkRoot.get("name")), params.getName().toLowerCase())));
            }
            String name = StringUtils.defaultIfBlank(params.getName(), "");
            Expression sortBy = cb.lower(linkRoot.get("name"));
            sortOrders.add(params.getSortOrder() == DESC ? cb.desc(sortBy) : cb.asc(sortBy));
        }

        // Complete the query
        query.select(linkRoot)
                .distinct(true)
                .where(criteriaHelper.where())
                .orderBy(sortOrders);

        // Execute the query and update the search result
        return em.createQuery(query)
                .setMaxResults(params.getMaxSize())
                .getResultList();
    }


    /**
     * Creates a new AtoN link from the given template.
     *
     * @param link the new AtoN link
     * @return the persisted AtoN link
     */
    @Transactional
    public AtonLink createAtonLink(AtonLink link) {

        // Ensure that is has a proper AtoN link ID
        link.checkAssignLinkId();

        AtonLink original = findAtonLink(link.getLinkId());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create AtoN link with duplicate AtoN link ID "
                    + link.getId());
        }

        // Replace the AtoNs with the persisted AtoNs
        final String[] peerUids = link.getPeers()
                .stream()
                .map(AtonNode::getAtonUid)
                .toArray(String[]::new);
        link.setPeers(new HashSet<>(this.atonService.findByAtonUids(peerUids)));

        log.info("Creating new AtoN link " + link.getLinkId());
        return saveEntity(link);
    }

    /**
     * Updates an existing AtoN link from the given template.
     *
     * @param link theAtoN link to update
     * @return the persisted AtoN link
     */
    @Transactional
    public AtonLink updateAtonLink(AtonLink link) {
        AtonLink original = findAtonLink(link.getLinkId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing AtoN link "
                    + link.getLinkId());
        }

        original.setName(link.getName());
        original.setLinkType(link.getLinkType());
        original.setLinkCategory(link.getLinkCategory());

        // Replace the AtoNs with the persisted AtoNs
        final String[] peerUids = link.getPeers()
                .stream()
                .map(AtonNode::getAtonUid)
                .toArray(String[]::new);
        original.setPeers(new HashSet<>(this.atonService.findByAtonUids(peerUids)));

        log.info("Updating AtoN link " + original.getLinkId());
        return saveEntity(original);
    }

    /**
     * Deletes the AtoN link with the given AtoN link ID.
     *
     * @param linkId the ID of the AtoN link to delete
     * @return if the AtoN link was deleted
     */
    @Transactional
    public boolean deleteAtonLink(UUID linkId) {

        AtonLink original = findAtonLink(linkId);
        if (original != null) {
            log.info("Removing AtoN link " + linkId);
            remove(original);
            return true;
        }
        return false;
    }

    /**
     * Clears all AtoN from the AtoN link with the given AtoN link ID.
     *
     * @param linkId the ID of the AtoN link to clear
     * @return if the AtoN link was clear
     */
    @Transactional
    public boolean clearAtonLink(UUID linkId) {

        AtonLink original = findAtonLink(linkId);
        if (original != null) {
            log.info("Clearing AtoN link " + linkId);
            original.getPeers().clear();
            saveEntity(original);
            return true;
        }
        return false;
    }


    /**
     * Adds AtoN to the given AtoN link.
     *
     * @param linkId the ID of the AtoN link to add the AtoN to
     * @param atonUids the UIDs of the AtoN to add
     * @return the updated AtoN link
     */
    @Transactional
    public AtonLink addAtonToAtonLink(UUID linkId, List<String> atonUids) {
        AtonLink link = findAtonLink(linkId);
        if (link == null) {
            throw new IllegalArgumentException("No AtoN link with link ID " + linkId);
        }

        int prevMsgCnt = link.getPeers().size();
        for (AtonNode atonNode : this.atonService.findByAtonUids(atonUids.toArray(String[]::new))) {
            link.getPeers().add(atonNode);
        }

        if (link.getPeers().size() != prevMsgCnt) {
            link = saveEntity(link);
            log.info("Added " + (link.getPeers().size() - prevMsgCnt) + " AtoN to AtoN link " + link.getName());
        }

        return link;
    }


    /**
     * Removes AtoN from the given AtoN link.
     *
     * @param linkId the ID of the AtoN link to remove the AtoN from
     * @param atonUids the UIDs the AtoN to remove
     * @return the updated AtoN link
     */
    @Transactional
    public AtonLink removeAtonFromAtonLink(UUID linkId, List<String> atonUids) {
        AtonLink link = findAtonLink(linkId);
        if (link == null) {
            throw new IllegalArgumentException("No AtoN link with ID " + linkId);
        }

        int prevMsgCnt = link.getPeers().size();
        for (AtonNode atonNode : this.atonService.findByAtonUids(atonUids.toArray(String[]::new))) {
            link.getPeers().remove(atonNode);
        }

        if (link.getPeers().size() != prevMsgCnt) {
            link = saveEntity(link);
            log.info("Removed " + (prevMsgCnt - link.getPeers().size()) + " AtoN from AtoN link " + link.getName());
        }

        return link;
    }

}
