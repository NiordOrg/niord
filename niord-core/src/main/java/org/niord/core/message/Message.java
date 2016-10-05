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
package org.niord.core.message;

import org.apache.commons.lang.StringUtils;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.model.DescEntity;
import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.message.AreaVo;
import org.niord.model.message.CategoryVo;
import org.niord.model.message.ChartVo;
import org.niord.model.message.MainType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessageVo;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.niord.model.DataFilter.Model.SYSTEM;


/**
 * The core message entity
 */
@Entity
@Table(indexes = {
        @Index(name = "message_uid_k", columnList="uid"),
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
        @NamedQuery(name="Message.findByShortId",
                query="SELECT msg FROM Message msg where msg.shortId = :shortId"),
        @NamedQuery(name="Message.findByMessageId",
                query="select distinct msg from Message msg where lower(msg.uid) = :msgId "
                        + " or lower(msg.shortId) = :msgId"),
        @NamedQuery(name="Message.findByReference",
                query="select distinct msg from Message msg join msg.references ref where "
                        + " lower(ref.messageId) in (:messageIds)"),
        @NamedQuery(name="Message.findPublishableMessages",
                query="SELECT msg FROM Message msg where msg.status = 'VERIFIED' and msg.publishDateFrom < :now"),
        @NamedQuery(name="Message.findExpirableMessages",
                query="SELECT msg FROM Message msg where msg.status = 'PUBLISHED' and msg.publishDateTo < :now"),
        @NamedQuery(name="Message.maxNumberInPeriod",
                query="SELECT coalesce(max(msg.number), 0) FROM Message msg where msg.messageSeries = :series and "
                        + " msg.publishDateFrom between :fromDate and :toDate and msg.number is not null")
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

    // Computed from dateInterval
    @Temporal(TemporalType.TIMESTAMP)
    Date eventDateFrom;

    // Computed from dateInterval
    @Temporal(TemporalType.TIMESTAMP)
    Date eventDateTo;

    @Temporal(TemporalType.TIMESTAMP)
    Date publishDateFrom;

    @Temporal(TemporalType.TIMESTAMP)
    Date publishDateTo;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<Reference> references = new ArrayList<>();

    @ManyToMany(mappedBy = "messages")
    List<MessageTag> tags = new ArrayList<>();

    Boolean originalInformation;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MessageDesc> descs = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<MessagePart> parts = new ArrayList<>();

    // Indicates if the title should automatically be updated from the message area, subject and vicinity fields.
    boolean autoTitle;

    boolean hasGeometry;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<Attachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "message")
    List<Comment> comments = new ArrayList<>();


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
        this.publishDateFrom = message.getPublishDateFrom();
        this.publishDateTo = message.getPublishDateTo();
        this.references.clear();
        if (message.getReferences() != null) {
            message.getReferences().stream()
                .filter(r -> StringUtils.isNotBlank(r.getMessageId()))
                .forEach(r -> addReference(new Reference(r)));
        }
        this.originalInformation = message.getOriginalInformation();
        if (message.getDescs() != null) {
            message.getDescs().forEach(desc -> addDesc(new MessageDesc(desc)));
        }
        if (message.getParts() != null) {
            message.getParts().forEach(part -> addPart(new MessagePart(part)));
            parts.removeIf(part -> !part.partDefined());
        }
        if (message.getAttachments() != null) {
            message.getAttachments().forEach(att -> addAttachment(new Attachment(att)));
        }

        if (message instanceof SystemMessageVo) {
            SystemMessageVo sysMessage = (SystemMessageVo) message;
            this.autoTitle = sysMessage.isAutoTitle() != null && sysMessage.isAutoTitle();
        }

        updateEventDateInterval();
    }


    /** Converts this entity to a value object */
    public MessageVo toVo(DataFilter filter) {

        MessageVo message = filter.forModel(SYSTEM)
                ? new SystemMessageVo()
                : new MessageVo();

        DataFilter compFilter = filter.forComponent(Message.class);

        message.setId(uid);
        message.setNumber(number);
        message.setShortId(shortId);
        message.setStatus(status);
        message.setMainType(mainType);
        message.setType(type);

        if (compFilter.includeDetails()) {
            message.setRepoPath(repoPath);
            message.setCreated(getCreated());
            message.setUpdated(getUpdated());
            if (messageSeries != null) {
                message.setMessageSeries(messageSeries.toVo(filter));
            }
            areas.forEach(a -> message.checkCreateAreas().add(a.toVo(AreaVo.class, filter)));
            categories.forEach(c -> message.checkCreateCategories().add(c.toVo(CategoryVo.class, filter)));
            charts.forEach(c -> message.checkCreateCharts().add(c.toVo(ChartVo.class, filter)));
            message.setHorizontalDatum(horizontalDatum);
            message.setPublishDateFrom(publishDateFrom);
            message.setPublishDateTo(publishDateTo);
            references.forEach(r -> message.checkCreateReferences().add(r.toVo(filter)));
            message.setOriginalInformation(originalInformation);
            attachments.forEach(att -> message.checkCreateAttachments().add(att.toVo(filter)));
        }
        if (compFilter.includeDetails() || compFilter.includeGeometry()) {
            parts.forEach(part -> message.checkCreateParts().add(part.toVo(compFilter)));
        }
        if (compFilter.anyOfFields(DataFilter.DETAILS, "MessageDesc.title")) {
            getDescs(compFilter).forEach(desc -> message.checkCreateDescs().add(desc.toVo(compFilter)));
        }

        if (filter.forModel(SYSTEM)) {
            SystemMessageVo systemMessage = (SystemMessageVo)message;
            systemMessage.setAutoTitle(autoTitle);

            message.sort(filter.getLang());

            long cnt = getComments().stream()
                    .filter(c -> c.getAcknowledgeDate() == null)
                    .count();
            systemMessage.setUnackComments((int)cnt);
        }

        return message;
    }


    /**
     * Whenever the message is persisted, re-compute the start and end dates.
     * <p>
     * Important:
     * This function is not annotated with @PreUpdate anymore. Instead, the function is called explicitly
     * from MessageService.saveMessage(). This is because we would occasionally see the following Hibernate error:
     * "AssertionFailure: collection owner not associated with session: org.niord.core.message.Reference.descs"
     * The error would depend on how many related entities (area, category, geometry, references,...) were defined
     * for the messages. Surely a Hibernate error, methinks...
     */
    @PrePersist
    //@PreUpdate
    public void onPersist() {

        if (uid == null) {
            assignNewUid(false);
        }
        repoPath = uidToMessageRepoPath(uid);

        // Set "area" to be the first area in the "areas" list
        area = areas.isEmpty() ? null : areas.get(0);

        // Update the main type from the type
        if (type != null) {
            mainType = type.getMainType();
        }

        // Remove blank references
        references.removeIf(r -> StringUtils.isBlank(r.getMessageId()));

        // Updates the start and end dates from the date intervals
        updateEventDateInterval();

        // Update the message title
        updateMessageTitle();

        // Update the has-geometry flag
        hasGeometry = parts.stream().anyMatch(p -> p.getGeometry() != null);
    }


    /** Compute the total event date interval **/
    public void updateEventDateInterval() {
        // Update event date intervals
        eventDateFrom = eventDateTo = null;

        parts.stream()
                .flatMap(p -> p.getEventDates().stream())
                .forEach(di -> {
            if (eventDateFrom == null || eventDateFrom.after(di.getFromDate())) {
                eventDateFrom = di.getFromDate();
            }
            if (eventDateTo == null || (di.getToDate() != null && eventDateTo.before(di.getToDate()))) {
                eventDateTo = di.getToDate();
            }
        });
    }


    /** Updates the title line of the message based on area, vicinity and subject */
    public void updateMessageTitle() {
        if (autoTitle) {
            // Get all involved message and message part languages
            Set<String> langs = Stream.concat(
                    getDescs().stream().map(DescEntity::getLang),
                    getParts().stream().flatMap(p -> p.getDescs().stream()).map(DescEntity::getLang)
            ).collect(Collectors.toSet());

            langs.forEach(lang -> {
                try {
                    // First add area lineage
                    StringBuilder title = new StringBuilder();
                    if (!getAreas().isEmpty()) {
                        title.append(Area.computeAreaTitlePrefix(getAreas(), lang));
                    }

                    // If defined, add vicinity
                    MessageDesc desc = getDesc(lang);
                    if (desc != null && StringUtils.isNotBlank(desc.getVicinity())) {
                        title.append(" ").append(desc.getVicinity());
                        if (!desc.getVicinity().endsWith(".")) {
                            title.append(".");
                        }
                    }

                    // Add the subject from each message part
                    parts.stream()
                            .map(p -> p.getDesc(lang))
                            .filter(d -> d != null && StringUtils.isNotBlank(d.getSubject()))
                            .forEach(d -> {
                                title.append(" ").append(d.getSubject());
                                if (!d.getSubject().endsWith(".")) {
                                    title.append(".");
                                }
                            });

                    // Update the message title
                    if (desc != null || StringUtils.isNotBlank(title.toString())) {
                        checkCreateDesc(lang).setTitle(title.toString().trim());
                    }
                } catch (Exception ignored) {
                }
            });

        }
    }


    /** Assigns a new UID to the Feature **/
    public String assignNewUid(boolean rewriteDescription) {
        String oldRepoPath = repoPath;

        uid = UUID.randomUUID().toString();
        repoPath = uidToMessageRepoPath(uid);

        // Rewrite any links pointing to the old repository path
        if (StringUtils.isNotBlank(oldRepoPath)) {
            String prefix = "\"/rest/repo/file/";
            getParts().stream()
                    .flatMap(part -> part.getDescs().stream())
                    .forEach(desc -> {
                        if (desc.getDetails() != null && desc.getDetails().contains(prefix + oldRepoPath)) {
                            desc.setDetails(desc.getDetails().replace(prefix + oldRepoPath, prefix + repoPath));
                        }
                    });
        }
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
     * Converts the uid to a message repository path.
     * <p>
     * To allow for a more efficient file directory structure,
     * the message repository folders are nested into two layers
     * of sub-directories. The hashing used for the directories
     * is based on the first 3 (1+2) characters of the UUID associated
     * with the messages, yielding approx 4K folders.
     *
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

        // Compose the repo path as "messages/uid[0;0]/uid[1;2]/uid"
        return String.format(
                "%s/%s/%s/%s",
                MESSAGE_REPO_FOLDER,
                uid.substring(0,1),
                uid.substring(1,3),
                uid);
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
    public MessageDesc addDesc(MessageDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
        return desc;
    }

    /** Adds a message part entity to this message */
    public MessagePart addPart(MessagePart part) {
        part.setMessage(this);
        parts.add(part);
        return part;
    }

    /** Returns the message parts with the given type */
    public List<MessagePart> partsByType(MessagePartType type) {
        return parts.stream()
                .filter(p -> type == p.getType())
                .collect(Collectors.toList());
    }

    /** Adds a reference to this message */
    public Reference addReference(Reference reference) {
        reference.setMessage(this);
        references.add(reference);
        return reference;
    }

    /** Adds an attachment to this message */
    public Attachment addAttachment(Attachment attachment) {
        attachment.setMessage(this);
        attachments.add(attachment);
        return attachment;
    }


    /** Returns all container GeoJSON geometries **/
    public FeatureCollectionVo[] toGeoJson() {
        return parts.stream()
                .filter(p -> p.getGeometry() != null)
                .filter(p -> !p.getGeometry().getFeatures().isEmpty())
                .map(p -> p.getGeometry().toGeoJson())
                .toArray(FeatureCollectionVo[]::new);
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

    public Date getEventDateFrom() {
        return eventDateFrom;
    }

    public void setEventDateFrom(Date eventDateFrom) {
        this.eventDateFrom = eventDateFrom;
    }

    public Date getEventDateTo() {
        return eventDateTo;
    }

    public void setEventDateTo(Date eventDateTo) {
        this.eventDateTo = eventDateTo;
    }

    public Date getPublishDateFrom() {
        return publishDateFrom;
    }

    public void setPublishDateFrom(Date publishDateFrom) {
        this.publishDateFrom = publishDateFrom;
    }

    public Date getPublishDateTo() {
        return publishDateTo;
    }

    public void setPublishDateTo(Date publishDateTo) {
        this.publishDateTo = publishDateTo;
    }

    public List<Reference> getReferences() {
        return references;
    }

    public void setReferences(List<Reference> references) {
        this.references = references;
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

    public List<MessagePart> getParts() {
        return parts;
    }

    public void setParts(List<MessagePart> parts) {
        this.parts = parts;
    }

    public boolean getHasGeometry() {
        return hasGeometry;
    }

    public void setHasGeometry(boolean hasGeometry) {
        this.hasGeometry = hasGeometry;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }
}
