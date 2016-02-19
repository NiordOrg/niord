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
package org.niord.core.service;

import org.niord.core.model.Aton;
import org.niord.core.model.AtonNode;
import org.niord.core.model.Extent;
import org.niord.model.PagedSearchResultVo;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Interface for handling AtoNs
 *
 * TODO: Make it a singleton and cache the list of AtoNs
 * or create a Lucene index with AtoNs
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class AtonService extends BaseService {

    @Inject
    private Logger log;

    /*************************/
    /** NEW Aton Model      **/
    /*************************/




    /**
     * Replaces the AtoN DB
     * @param atons the new AtoNs
     */
    @Lock(LockType.WRITE)
    public void replaceAtons(List<AtonNode> atons) {

        // Delete old AtoNs
        int deleted = em
                .createNamedQuery("AtonNode.deleteAll")
                .executeUpdate();

        log.info("Deleted " + deleted + " AtoNs");
        em.flush();

        // Persist new list of AtoNs
        long t0 = System.currentTimeMillis();
        int x = 0;
        for (AtonNode aton : atons) {
            em.persist(aton);

            if (x++ % 100 == 0) {
                em.flush();
            }
        }
        log.info("Persisted " + atons.size() + " AtoNs in " +
                (System.currentTimeMillis() - t0) + " ms");
    }





    /*************************/
    /** OLD Aton Model      **/
    /*************************/


    private List<Aton> atons = new ArrayList<>();

    /** Reloads the list of AtoNs */
    @PostConstruct
    @Lock(LockType.WRITE)
    void loadAtoNs() {
        atons = getAll(Aton.class);
    }

    /**
     * Returns the AtoN with the given ID
     * @param atonUid the AtoN ID
     * @return the AtoN with the given ID
     */
    public Aton getAton(String atonUid) {
        return atons.stream()
                .filter(a -> a.getAtonUid().equals(atonUid))
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns the list of all AtoNs
     * @return the list of all AtoNs
     */
    public List<Aton> getAllAtons() {
        return atons;
    }


    /**
     * Replaces the AtoN DB
     * @param atons the new AtoNs
     */
    @Lock(LockType.WRITE)
    public void replaceAtonsOld(List<Aton> atons) {

        // Delete old AtoNs
        int deleted = em.createNamedQuery("Aton.deleteAll").executeUpdate();
        log.info("Deleted " + deleted + " AtoNs");
        em.flush();

        // Persist new list of AtoNs
        long t0 = System.currentTimeMillis();
        int x = 0;
        for (Aton aton : atons) {
            em.persist(aton);

            if (x++ % 100 == 0) {
                em.flush();
            }
        }
        log.info("Persisted " + atons.size() + " AtoNs in " +
                (System.currentTimeMillis() - t0) + " ms");

        // Reload the AtoN list
        loadAtoNs();
    }


    /**
     * Computes the list of AtoNs within the given name or mapExtents.<br>
     *
     * @return the AtoNs within the given name or mapExtents
     */
    public PagedSearchResultVo<Aton> search(AtonSearchParams param) {
        try {
            return PagedSearchResultVo.paginate(
                    getAllAtons(),
                    param.getPage(),
                    param.getMaxSize(),
                    a -> matchText(a, param.getName())
                            && withinExtent(a, param.getMapExtents())
                            && withinExtent(a, param.getChartExtents()),
                    null
            );

        } catch (RuntimeException e) {
            log.error("Error computing AtoNs within the extents");
            return new PagedSearchResultVo<>();
        }
    }


    /** Returns whether the AtoN matches the given text **/
    boolean matchText(Aton aton, String txt) {
        return StringUtils.isBlank(txt) ||
                aton.getAtonUid().toLowerCase().contains(txt.toLowerCase()) ||
                (aton.getName() != null && aton.getName().toLowerCase().contains(txt.toLowerCase()));
    }


    /** Returns if the AtoN is contained in any of the given mapExtents */
    boolean withinExtent(Aton aton, List<Extent> extents) {
        return extents == null ||
                extents.stream()
                .anyMatch(e -> e.withinExtent(aton.getLat(), aton.getLon()));
    }

}
