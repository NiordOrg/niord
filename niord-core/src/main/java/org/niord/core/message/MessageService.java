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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.service.BaseService;
import org.niord.core.user.UserService;
import org.niord.model.DataFilter;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business interface for managing messages
 */
@Stateless
@SuppressWarnings("unused")
public class MessageService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    UserService userService;

    @Inject
    MessageSeriesService messageSeriesService;


    /***************************************/
    /** Message methods                   **/
    /***************************************/


    /**
     * Returns the message with the given id
     *
     * @param id the id of the message
     * @return the message with the given id or null if not found
     */
    public Message findById(Integer id) {
        return getByPrimaryKey(Message.class, id);
    }


    /**
     * Saves the message and evicts the message from the cache
     *
     * @param message the message to save
     * @return the saved message
     */
    public Message saveMessage(Message message) {
        boolean wasPersisted = message.isPersisted();

        // Save the message
        message = saveEntity(message);

        // Save a MessageHistory entity for the message
        saveHistory(message);

        return message;
    }


    /**
     * Creates a new message as a draft message
     *
     * @param message the template for the message to create
     * @return the new message
     */
    public Message createMessage(Message message) throws Exception {

        // Validate the message
        if (message.isPersisted()) {
            throw new Exception("Message already persisted");
        }
        if (message.getMessageSeries() == null) {
            throw new Exception("Message series not specified");
        }
        if (message.getType() == null) {
            throw new Exception("Missing Message type");
        }
        message.updateStartAndEndDates();
        if (message.getStartDate() == null) {
            throw new Exception("Message start date must be specified");
        }

        // Set default values
        message.setStatus(Status.DRAFT);

        // Substitute the Area with a persisted one
        message.setAreas(persistedList(Area.class, message.getAreas()));

        // Substitute the Categories with the persisted ones
        message.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        message.setCharts(persistedList(Chart.class, message.getCharts()));

        // Persist the message
        message = saveMessage(message);
        log.info("Saved message " + message);

        em.flush();
        return message;
    }


    /**
     * Updates the given message
     *
     * @param message the template for the message to update
     * @return the updated message
     */
    public Message updateMessage(Message message) throws Exception {

        Message original = findById(message.getId());

        // Validate the message
        if (original == null) {
            throw new Exception("Message not an existing message");
        }

        original.setMessageSeries(null);
        if (message.getMessageSeries() != null) {
            original.setMessageSeries(getByPrimaryKey(MessageSeries.class, message.getMessageSeries().getId()));
        }
        original.setNumber(message.getNumber());
        original.setMrn(message.getMrn());
        original.setShortId(message.getShortId());
        original.setType(message.getType());
        original.setStatus(message.getStatus());

        // Substitute the Area with a persisted one
        original.setAreas(persistedList(Area.class, message.getAreas()));

        // Substitute the Categories with the persisted ones
        original.setCategories(persistedList(Category.class, message.getCategories()));

        // Substitute the Charts with the persisted ones
        original.setCharts(persistedList(Chart.class, message.getCharts()));

        original.setHorizontalDatum(message.getHorizontalDatum());
        original.setGeometry(message.getGeometry());

        original.setPublishDate(message.getPublishDate());
        original.setCancellationDate(message.getCancellationDate());

        original.setDateIntervals(message.getDateIntervals().stream()
            .map(di -> di.isNew() ? di : getByPrimaryKey(DateInterval.class, di.getId()))
            .collect(Collectors.toList()));
        original.updateStartAndEndDates();

        original.setReferences(message.getReferences().stream()
            .map(r -> r.isNew() ? r : getByPrimaryKey(Reference.class, r.getId()))
            .collect(Collectors.toSet()));

        original.getAtonUids().clear();
        original.getAtonUids().addAll(message.getAtonUids());

        original.setOriginalInformation(message.getOriginalInformation());

        // Copy the area data
        original.copyDescsAndRemoveBlanks(message.getDescs());

        // Persist the message
        original = saveMessage(original);
        log.info("Updated message " + original);

        em.flush();
        return original;
    }


    /**
     * Updates the status of the given message
     *
     * @param messageId the id of the message
     * @param status    the status
     */
    public Message updateStatus(Integer messageId, Status status) {
        Message message = findById(messageId);
        Status prevStatus = message.getStatus();

        if ((prevStatus == Status.DRAFT || prevStatus == Status.VERIFIED) && status == Status.PUBLISHED) {
            messageSeriesService.createNewIdentifiers(message);
        }

        message.setStatus(status);
        message = saveMessage(message);
        return message;
    }


    /***************************************/
    /** Message History methods           **/
    /***************************************/


    /**
     * Saves a history entity containing a snapshot of the message
     *
     * @param message the message to save a snapshot for
     */
    public void saveHistory(Message message) {

        try {
            MessageHistory hist = new MessageHistory();
            hist.setMessage(message);
            hist.setUser(userService.currentUser());
            hist.setStatus(message.getStatus());
            hist.setCreated(new Date());
            hist.setVersion(message.getVersion() + 1);

            // Create a snapshot of the message
            ObjectMapper jsonMapper = new ObjectMapper();
            MessageVo snapshot = message.toVo(DataFilter.get().fields("Message.details"));
            hist.setSnapshot(jsonMapper.writeValueAsString(snapshot));

            saveEntity(hist);

        } catch (Exception e) {
            log.error("Error saving a history entry for message " + message.getId(), e);
            // NB: Don't propagate errors
        }
    }

    /**
     * Returns the message history for the given message ID
     *
     * @param messageId the message ID
     * @return the message history
     */
    public List<MessageHistory> getMessageHistory(Integer messageId) {
        return em.createNamedQuery("MessageHistory.findByMessageId", MessageHistory.class)
                .setParameter("messageId", messageId)
                .getResultList();
    }

}
