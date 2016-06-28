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
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageVo;
import org.niord.model.vo.Status;
import org.niord.model.vo.Type;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderColumn;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;


/**
 * The core message entity
 */
@Entity
@Table(indexes = {
        @Index(name = "message_uid_k", columnList="uid"),
        @Index(name = "message_mrn_k", columnList="mrn"),
        @Index(name = "message_type_k", columnList="type"),
        @Index(name = "message_main_type_k", columnList="mainType"),
        @Index(name = "message_status_k", columnList="status"),
        @Index(name = "message_legacy_id_k", columnList="legacyId")
})
@NamedQueries({
        @NamedQuery(name="Message.findUpdateMessages",
                query="SELECT msg FROM Message msg where msg.updated > :date order by msg.updated asc"),
        @NamedQuery(name="Message.findByUid",
                query="SELECT msg FROM Message msg where msg.uid = :uid"),
        @NamedQuery(name="Message.findByUids",
                query="SELECT msg FROM Message msg where msg.uid in (:uids)"),
        @NamedQuery(name="Message.findByIds",
                query="SELECT msg FROM Message msg where msg.id in (:ids)"),
        @NamedQuery(name="Message.findByLegacyId",
                query="SELECT msg FROM Message msg where msg.legacyId = :legacyId"),
        @NamedQuery(name="Message.findByMrn",
                query="SELECT msg FROM Message msg where msg.mrn = :mrn"),
        @NamedQuery(name="Message.findByShortId",
                query="SELECT msg FROM Message msg where msg.shortId = :shortId"),
        @NamedQuery(name  = "Message.searchShortIds",
                query="select distinct m from Message m where lower(m.shortId) like lower(:term) "
                        + "order by locate(lower(:sort), lower(m.shortId)) asc "),
        @NamedQuery(name  = "Message.searchMrn",
                query="select distinct m from Message m where lower(m.mrn) like lower(:term) "
                        + "order by locate(lower(:sort), lower(m.mrn)) asc ")
})
@SuppressWarnings("unused")
public class Message extends VersionedEntity<Integer> implements ILocalizable<MessageDesc> {

    public static String MESSAGE_REPO_FOLDER = "messages";

    @Column(nullable = false, unique = true, length = 36)
    String uid;

    @Column(nullable = false, unique = true, length = 128)
    String repoPath;

    @ManyToOne
    MessageSeries messageSeries;

    String legacyId;

    Integer number;

    // Globally unique
    String mrn;

    // Unique within the current message series
    String shortId;

    @NotNull
    @Enumerated(EnumType.STRING)
    MainType mainType;

    @NotNull
    @Enumerated(EnumType.STRING)
    Type type;

    @NotNull
    @Enumerated(EnumType.STRING)
    Status status;

    @ManyToMany
    @OrderColumn
    List<Area> areas = new ArrayList<>();

    // This area should be the first area of the "areas" list.
    // Not very normalized, but it makes it easier and more efficient to perform searches.
    // The reference is updated in the @PrePersist method.
    @ManyToOne
    Area area;

    // The areaSortOrder is used to sort the message within its associated area
    @Column(columnDefinition = "DOUBLE default 0.0")
    double areaSortOrder;

    @ManyToMany
    List<Category> categories = new ArrayList<>();

    @ManyToMany
    @OrderColumn
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
    @OrderColumn(name = "indexNo")
    List<DateInterval> dateIntervals = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<Reference> references = new ArrayList<>();

    // As Niord does not "own" the AtoN data, use weak references to AtoNs
    @ElementCollection
    List<String> atonUids = new ArrayList<>();

    @ManyToMany(mappedBy = "messages")
    List<MessageTag> tags = new ArrayList<>();

    Boolean originalInformation;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MessageDesc> descs = new ArrayList<>();

    // Indicates if the title should automatically be updated from the message area, subject and vicinity fields.
    boolean autoTitle;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<Attachment> attachments = new ArrayList<>();

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
        setCreated(message.getCreated());
        setUpdated(message.getUpdated());
        this.uid = message.getId();
        this.repoPath = uidToMessageRepoPath(uid);
        this.number = message.getNumber();
        this.mrn = message.getMrn();
        this.shortId = message.getShortId();
        this.mainType = message.getMainType();
        this.type = message.getType();
        this.status = message.getStatus();
        this.areas.clear();
        if (message.getAreas() != null) {
            message.getAreas().forEach(a -> areas.add(new Area(a, compFilter)));
        }
        this.categories.clear();
        if (message.getCategories() != null) {
            message.getCategories().forEach(c -> categories.add(new Category(c, compFilter)));
        }
        this.charts.clear();
        if (message.getCharts() != null) {
            message.getCharts().forEach(c -> charts.add(new Chart(c)));
        }
        this.horizontalDatum = message.getHorizontalDatum();
        if (message.getGeometry() != null) {
            this.geometry = FeatureCollection.fromGeoJson(message.getGeometry());
        }
        this.dateIntervals.clear();
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
            message.getDescs().forEach(desc -> addDesc(new MessageDesc(desc)));
        }
        if (message.getAttachments() != null) {
            message.getAttachments().forEach(att -> addAttachment(new Attachment(att)));
        }

        if (message instanceof EditableMessageVo) {
            EditableMessageVo editableMessage = (EditableMessageVo) message;
            this.autoTitle = editableMessage.isAutoTitle() != null && editableMessage.isAutoTitle();
        }

        updateDateIntervals();
    }


    /** Converts this entity to a value object */
    private <M extends MessageVo> M toVo(M message, DataFilter filter) {

        DataFilter compFilter = filter.forComponent(Message.class);

        message.setId(uid);
        message.setNumber(number);
        message.setMrn(mrn);
        message.setShortId(shortId);
        message.setMainType(mainType);
        message.setType(type);

        if (compFilter.includeField(DataFilter.DETAILS)) {
            message.setRepoPath(repoPath);
            message.setCreated(getCreated());
            message.setUpdated(getUpdated());
            message.setVersion(getVersion());
            if (messageSeries != null) {
                message.setMessageSeries(messageSeries.toVo(filter));
            }
            message.setStatus(status);
            areas.forEach(a -> message.checkCreateAreas().add(a.toVo(filter)));
            categories.forEach(c -> message.checkCreateCategories().add(c.toVo(filter)));
            charts.forEach(c -> message.checkCreateCharts().add(c.toVo(filter)));
            message.setHorizontalDatum(horizontalDatum);
            dateIntervals.forEach(d -> message.checkCreateDateIntervals().add(d.toVo()));
            message.setPublishDate(publishDate);
            message.setCancellationDate(cancellationDate);
            message.setStartDate(startDate);
            message.setEndDate(endDate);
            references.forEach(r -> message.checkCreateReferences().add(r.toVo()));
            message.checkCreateAtonUids().addAll(atonUids);
            message.setOriginalInformation(originalInformation);
            attachments.forEach(att -> message.checkCreateAttachments().add(att.toVo()));
        }
        if (compFilter.anyOfFields(DataFilter.GEOMETRY) && geometry != null) {
            message.setGeometry(geometry.toGeoJson());
            GeoJsonUtils.setLanguage(message.getGeometry(), compFilter.getLang(), false);
        }
        if (compFilter.anyOfFields(DataFilter.DETAILS, "MessageDesc.title")) {
            getDescs(compFilter).forEach(desc -> message.checkCreateDescs().add(desc.toVo(compFilter)));
        }

        return message;
    }


    /** Converts this entity to a value object */
    public MessageVo toVo(DataFilter filter) {
        return toVo(new MessageVo(), filter);
    }


    /** Converts this entity to a value object */
    public EditableMessageVo toEditableVo(DataFilter filter) {

        EditableMessageVo message = toVo(new EditableMessageVo(), filter);

        message.setAutoTitle(autoTitle);

        return message;
    }


    /** Whenever the message is persisted, re-compute the start and end dates */
    @PrePersist
    protected void onPersist() {

        if (uid == null) {
            assignNewUid();
        }
        repoPath = uidToMessageRepoPath(uid);

        // Set "area" to be the first area in the "areas" list
        area = areas.isEmpty() ? null : areas.get(0);

        // Update the main type from the type
        if (type != null) {
            mainType = type.getMainType();
        }

        // Updates the start and end dates from the date intervals
        updateDateIntervals();

        // Update the message title
        updateMessageTitle();
    }


    /** Updates the date intervals **/
    public void updateDateIntervals() {
        // First, remove all date intervals without a fromDate or a toDate
        dateIntervals.removeIf(di -> di.fromDate == null && di.toDate == null);

        // If the validity of the date intervals, and that the all-day flag is adhered to
        dateIntervals.forEach(DateInterval::checkDateInterval);

        // Sort the date interval
        Collections.sort(dateIntervals);

        // Update start and endDate from the data intervals
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


    /** Updates the title line of the message based on area, vicinity and subject */
    public void updateMessageTitle() {
        if (autoTitle) {
            getDescs().forEach(desc -> {
                try {
                    StringBuilder title = new StringBuilder();
                    if (!getAreas().isEmpty()) {
                        title.append(Area.computeAreaTitlePrefix(getAreas(), desc.getLang()));
                    }
                    if (StringUtils.isNotBlank(desc.getVicinity())) {
                        title.append(" ").append(desc.getVicinity());
                        if (!desc.getVicinity().endsWith(".")) {
                            title.append(".");
                        }
                    }
                    if (StringUtils.isNotBlank(desc.getSubject())) {
                        title.append(" ").append(desc.getSubject());
                        if (!desc.getSubject().endsWith(".")) {
                            title.append(".");
                        }
                    }
                    desc.setTitle(title.toString().trim());
                } catch (Exception ignored) {
                }
            });

        }
    }


    /** Assigns a new UID to the Feature **/
    public String assignNewUid() {
        uid = UUID.randomUUID().toString();
        repoPath = uidToMessageRepoPath(uid);
        return uid;
    }


    /**
     * Validates that the uid has the proper UUID format
     * @param uid the UID to validate
     * @return if the UID has a valid UUID format
     */
    public static boolean validUidFormat(String uid) {
        try{
            UUID uuid = UUID.fromString(uid);
            return true;
        } catch (IllegalArgumentException exception){
            return false;
        }
    }


    /**
     * Converts the uid to a message repository path
     * @param uid the UID
     * @return the associated message repository path
     */
    public static String uidToMessageRepoPath(String uid) {
        if (StringUtils.isBlank(uid)) {
            return null;
        }
        if (!validUidFormat(uid)) {
            throw new IllegalArgumentException("Invalid UID format " + uid);
        }
        String[] parts = uid.split("-");
        return String.format(
                "%s/%s/%s/%s-%s-%s",
                MESSAGE_REPO_FOLDER,
                parts[0], parts[1], parts[2], parts[3], parts[4]);
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


    /** Adds a description entity to this message */
    public void addDesc(MessageDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
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

    /** Adds an attachment to this message */
    public void addAttachment(Attachment attachment) {
        attachment.setMessage(this);
        attachments.add(attachment);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

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

    public String getLegacyId() {
        return legacyId;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public MainType getMainType() {
        return mainType;
    }

    public void setMainType(MainType mainType) {
        this.mainType = mainType;
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

    public Area getArea() {
        return area;
    }

    public void setArea(Area area) {
        this.area = area;
    }

    public double getAreaSortOrder() {
        return areaSortOrder;
    }

    public void setAreaSortOrder(double areaSortOrder) {
        this.areaSortOrder = areaSortOrder;
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

    public List<Reference> getReferences() {
        return references;
    }

    public void setReferences(List<Reference> references) {
        this.references = references;
    }

    public List<String> getAtonUids() {
        return atonUids;
    }

    public void setAtonUids(List<String> atonUids) {
        this.atonUids = atonUids;
    }

    public List<MessageTag> getTags() {
        return tags;
    }

    public void setTags(List<MessageTag> tags) {
        this.tags = tags;
    }

    public Boolean getOriginalInformation() {
        return originalInformation;
    }

    public void setOriginalInformation(Boolean originalInformation) {
        this.originalInformation = originalInformation;
    }

    public boolean isAutoTitle() {
        return autoTitle;
    }

    public void setAutoTitle(boolean autoTitle) {
        this.autoTitle = autoTitle;
    }

    @Override
    public List<MessageDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessageDesc> descs) {
        this.descs = descs;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }
}
