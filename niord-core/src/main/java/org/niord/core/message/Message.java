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

import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.niord.model.vo.Type;

import javax.persistence.CascadeType;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * The core message entity
 */
@Entity
@Table(indexes = {
        @Index(name = "message_mrn_k", columnList="mrn")
})
@SuppressWarnings("unused")
public class Message extends VersionedEntity<Integer> implements ILocalizable<MessageDesc> {

    @ManyToOne
    MessageSeries messageSeries;

    Integer number;

    // Globally unique
    String mrn;

    // Unique within the current message series
    String shortId;

    @NotNull
    @Enumerated(EnumType.STRING)
    Type type;

    @NotNull
    @Enumerated(EnumType.STRING)
    Status status;

    @ManyToMany
    List<Area> areas = new ArrayList<>();

    @ManyToMany
    List<Category> categories = new ArrayList<>();

    @ManyToMany
    List<Chart> charts = new ArrayList<>();

    String horizontalDatum;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    FeatureCollection geometry;

    // Computed from dateInterval
    @Temporal(TemporalType.TIMESTAMP)
    Date startDate;

    // Computed from dateInterval
    @Temporal(TemporalType.TIMESTAMP)
    Date endDate;

    @Temporal(TemporalType.TIMESTAMP)
    Date publishDate;

    @Temporal(TemporalType.TIMESTAMP)
    Date cancellationDate;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    List<DateInterval> dateIntervals = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    Set<Reference> references = new HashSet<>();

    // As Niord does not "own" the AtoN data, use weak references to AtoNs
    @ElementCollection
    List<String> atonUids = new ArrayList<>();

    Boolean originalInformation;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MessageDesc> descs = new ArrayList<>();


    /**
     * Constructor
     */
    public Message() {
    }


    /**
     * Constructor
     */
    public Message(MessageVo message) {
        this(message, DataFilter.get());
    }


    /**
     * Constructor
     */
    public Message(MessageVo message, DataFilter filter) {
        updateMessage(message, filter);
    }


    /**
     * Updates this entity from the message according to the filter
     */
    public void updateMessage(MessageVo message, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Message.class);

        if (message.getMessageSeries() != null) {
            this.messageSeries = new MessageSeries(message.getMessageSeries());
        }
        this.id = message.getId();
        this.number = message.getNumber();
        this.mrn = message.getMrn();
        this.shortId = message.getShortId();
        this.type = message.getType();
        this.status = message.getStatus();
        if (message.getAreas() != null) {
            message.getAreas().forEach(a -> areas.add(new Area(a, compFilter)));
        }
        if (message.getCategories() != null) {
            message.getCategories().forEach(c -> categories.add(new Category(c, compFilter)));
        }
        if (message.getCharts() != null) {
            message.getCharts().forEach(c -> charts.add(new Chart(c)));
        }
        this.horizontalDatum = message.getHorizontalDatum();
        if (message.getGeometry() != null) {
            this.geometry = FeatureCollection.fromGeoJson(message.getGeometry());
        }
        if (message.getDateIntervals() != null) {
            message.getDateIntervals().forEach(di -> addDateInterval(new DateInterval(di)));
        }
        this.publishDate = message.getPublishDate();
        if (message.getReferences() != null) {
            message.getReferences().forEach(r -> addReference(new Reference(r)));
        }
        if (message.getAtonUids() != null) {
            atonUids.addAll(message.getAtonUids());
        }
        this.originalInformation = message.getOriginalInformation();
        if (message.getDescs() != null) {
            message.getDescs().forEach(desc -> createDesc(desc.getLang()).copyDesc(desc));
        }

        updateStartAndEndDates();
    }


    /** Converts this entity to a value object */
    public MessageVo toVo(DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Message.class);
        MessageVo message = new MessageVo();

        message.setId(id);
        message.setNumber(number);
        message.setMrn(mrn);
        message.setShortId(shortId);
        message.setType(type);

        if (compFilter.includeField(DataFilter.DETAILS)) {
            message.setCreated(getCreated());
            message.setUpdated(getUpdated());
            message.setVersion(getVersion());
            if (messageSeries != null) {
                message.setMessageSeries(messageSeries.toVo());
            }
            message.setStatus(status);
            areas.forEach(a -> message.checkCreateAreas().add(a.toVo(compFilter)));
            categories.forEach(c -> message.checkCreateCategories().add(c.toVo(compFilter)));
            charts.forEach(c -> message.checkCreateCharts().add(c.toVo()));
            message.setHorizontalDatum(horizontalDatum);
            dateIntervals.forEach(d -> message.checkCreateDateIntervals().add(d.toVo()));
            message.setPublishDate(publishDate);
            message.setCancellationDate(cancellationDate);
            references.forEach(r -> message.checkCreateReferences().add(r.toVo()));
            message.checkCreateAtonUids().addAll(atonUids);
            message.setOriginalInformation(originalInformation);
        }
        if (compFilter.anyOfFields(DataFilter.DETAILS, DataFilter.GEOMETRY) && geometry != null) {
            message.setGeometry(geometry.toGeoJson());
        }
        if (compFilter.anyOfFields(DataFilter.DETAILS, "MessageDesc.title")) {
            getDescs(compFilter).stream()
                    .forEach(desc -> message.checkCreateDescs().add(desc.toVo(compFilter)));
        }

        return message;
    }


    /** Whenever the message is persisted, re-compute the start and end dates */
    @PrePersist
    protected void updateStartAndEndDates() {
        startDate = endDate = null;
        dateIntervals.forEach(di -> {
            if (startDate == null || startDate.after(di.getFromDate())) {
                startDate = di.getFromDate();
            }
            if (endDate == null || (di.getToDate() != null && endDate.before(di.getToDate()))) {
                endDate = di.getToDate();
            }
        });
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public MessageDesc createDesc(String lang) {
        MessageDesc desc = new MessageDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a reference to this message */
    public void addReference(Reference reference) {
        reference.setMessage(this);
        references.add(reference);
    }

    /** Adds a date interval to this message */
    public void addDateInterval(DateInterval dateInterval) {
        dateInterval.setMessage(this);
        dateIntervals.add(dateInterval);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public MessageSeries getMessageSeries() {
        return messageSeries;
    }

    public void setMessageSeries(MessageSeries messageSeries) {
        this.messageSeries = messageSeries;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getMrn() {
        return mrn;
    }

    public void setMrn(String mrn) {
        this.mrn = mrn;
    }

    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<Area> getAreas() {
        return areas;
    }

    public void setAreas(List<Area> areas) {
        this.areas = areas;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public List<Chart> getCharts() {
        return charts;
    }

    public void setCharts(List<Chart> charts) {
        this.charts = charts;
    }

    public String getHorizontalDatum() {
        return horizontalDatum;
    }

    public void setHorizontalDatum(String horizontalDatum) {
        this.horizontalDatum = horizontalDatum;
    }

    public FeatureCollection getGeometry() {
        return geometry;
    }

    public void setGeometry(FeatureCollection geometry) {
        this.geometry = geometry;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Date getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(Date publishDate) {
        this.publishDate = publishDate;
    }

    public Date getCancellationDate() {
        return cancellationDate;
    }

    public void setCancellationDate(Date cancellationDate) {
        this.cancellationDate = cancellationDate;
    }

    public List<DateInterval> getDateIntervals() {
        return dateIntervals;
    }

    public void setDateIntervals(List<DateInterval> dateIntervals) {
        this.dateIntervals = dateIntervals;
    }

    public Set<Reference> getReferences() {
        return references;
    }

    public void setReferences(Set<Reference> references) {
        this.references = references;
    }

    public List<String> getAtonUids() {
        return atonUids;
    }

    public void setAtonUids(List<String> atonUids) {
        this.atonUids = atonUids;
    }

    public Boolean getOriginalInformation() {
        return originalInformation;
    }

    public void setOriginalInformation(Boolean originalInformation) {
        this.originalInformation = originalInformation;
    }

    @Override
    public List<MessageDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessageDesc> descs) {
        this.descs = descs;
    }

}
