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
package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Business interface for accessing messages
 */
@Stateless
public class MessageService extends BaseService {

    @Inject
    private Logger log;


    /****************************/
    /** Message Series         **/
    /****************************/

    /**
     * Returns the message series with the given MRN format
     * @param mrnFormat the MRN format
     * @return the message series with the given MRN format or null if not found
     */
    public MessageSeries findByMrnFormat(String mrnFormat) {
        try {
            return em.createNamedQuery("MessageSeries.findByMrnFormat", MessageSeries.class)
                    .setParameter("mrnFormat", mrnFormat)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Returns all message series with the given ids
     * @param ids the ids of the message series to look up
     * @return the list of all message series with the given ids
     */
    public List<MessageSeries> findByIds(Set<Integer> ids) {
        return em.createNamedQuery("MessageSeries.findByIds", MessageSeries.class)
                .setParameter("ids", ids)
                .getResultList();
    }


    /**
     * Returns all message series
     * @return the list of all message series
     */
    public List<MessageSeries> getAllMessageSeries() {
        return getAll(MessageSeries.class);
    }


    /**
     * Searches for message series matching the given term
     *
     * @param term the search term
     * @param limit the maximum number of results
     * @return the search result
     */
    public List<MessageSeries> searchMessageSeries(String term, int limit) {

        if (StringUtils.isNotBlank(term)) {
            return em
                    .createNamedQuery("MessageSeries.searchMessageSeries", MessageSeries.class)
                    .setParameter("term", "%" + term + "%")
                    .setMaxResults(limit)
                    .getResultList();
        }
        return Collections.emptyList();
    }


    /**
     * Creates a new message series from the given template
     * @param series the new message series
     * @return the persisted message series
     */
    public MessageSeries createMessageSeries(MessageSeries series) {
        MessageSeries original = findByMrnFormat(series.getMrnFormat());
        if (original != null) {
            throw new IllegalArgumentException("Cannot create message series with duplicate MRN format "
                    + series.getMrnFormat());
        }
        return saveEntity(series);
    }


    /**
     * Updates an existing message series from the given template
     * @param series the message series to update
     * @return the persisted message series
     */
    public MessageSeries updateMessageSeries(MessageSeries series) {
        MessageSeries original = getByPrimaryKey(MessageSeries.class, series.getId());

        original.setMrnFormat(series.getMrnFormat());
        original.setMainType(series.getMainType());
        original.setShortFormat(series.getShortFormat());

        return saveEntity(original);
    }


    /**
     * Deletes the message series with the given ID
     * @param id the message series to delete
     * @return if the message was deleted
     */
    public boolean deleteMessageSeries(Integer id) {

        // TODO: Check if there are message with the given message series

        MessageSeries original = getByPrimaryKey(MessageSeries.class, id);
        if (original != null) {
            remove(original);
            return true;
        }
        return false;
    }

}
