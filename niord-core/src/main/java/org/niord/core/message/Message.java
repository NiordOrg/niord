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
import org.niord.core.promulgation.BaseMessagePromulgation;
import org.niord.core.promulgation.vo.BaseMessagePromulgationVo;
import org.niord.core.user.User;
import org.niord.core.util.TimeUtils;
import org.niord.core.util.UidUtils;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.FeatureVo;
import org.niord.model.message.AreaVo;
import org.niord.model.message.CategoryVo;
import org.niord.model.message.ChartVo;
import org.niord.model.message.MainType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.MessageSeriesVo;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
        @NamedQuery(name="Message.maxNumberInPeriod",
                query="SELECT coalesce(max(msg.number), 0) FROM Message msg where msg.messageSeries = :series and "
                        + " msg.publishDateFrom between :fromDate and :toDate and msg.number is not null"),
        @NamedQuery(name="Message.separatePageUids",
                query="SELECT msg.uid FROM Message msg where msg.separatePage = true and msg.uid in (:uids)"),
})
@SuppressWarnings("unused")
public class Message extends VersionedEntity<Integer> implements ILocalizable<MessageDesc> {

    public static String MESSAGE_REPO_FOLDER = "messages";
    public static final DataFilter MESSAGE_DETAILS_FILTER =
            DataFilter.get().fields("Message.details", "Message.geometry", "Area.parent", "Category.parent");
    public static final DataFilter MESSAGE_DETAILS_AND_PROMULGATIONS_FILTER =
            DataFilter.get().fields("Message.details", "Message.geometry", "Message.promulgations", "Area.parent", "Category.parent");
    public static final DataFilter MESSAGE_MAP_FILTER =
            DataFilter.get().fields("Message.geometry", "MessageDesc.title");

    @ManyToOne
    User createdBy;

    @ManyToOne
    User lastUpdatedBy;

    @Column(nullable = false, unique = true, length = 36)
    String uid;

    // Unlike Message.version, which is used to control optimistic locking, the Message.revision
    // attribute is used define the repo-path sub-folder used for attachments when a message is edited.
    int revision;

    String thumbnailPath;

    @Column(nullable = false, unique = true, length = 128)
    String repoPath;

    @ManyToOne
    MessageSeries messageSeries;

    String legacyId;

    // Redundant because of publishDateFrom, but handy for sorting by ID
    Integer year;

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

    @Temporal(TemporalType.TIMESTAMP)
    Date followUpDate;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<Reference> references = new ArrayList<>();

    @ManyToMany(mappedBy = "messages")
    List<MessageTag> tags = new ArrayList<>();

    @OneToMany(mappedBy = "message")
    List<MessageHistory> history = new ArrayList<>();

    Boolean originalInformation;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<MessageDesc> descs = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<MessagePart> parts = new ArrayList<>();

    // Indicates if the title should automatically be updated from the message area, subject and vicinity fields.
    boolean autoTitle;

    // Not very normalized, but makes it easier to perform searches
    boolean hasGeometry;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "message", orphanRemoval = true)
    @OrderColumn(name = "indexNo")
    List<Attachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "message")
    List<Comment> comments = new ArrayList<>();

    // Whether to start the message on a new PDF page (for large messages)
    Boolean separatePage;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    List<BaseMessagePromulgation> promulgations = new ArrayList<>();


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
        this.repoPath = UidUtils.uidToHashedFolderPath(MESSAGE_REPO_FOLDER, uid);
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
        this.followUpDate = message.getFollowUpDate();
        this.references.clear();
        if (message.getReferences() != null) {
            message.getReferences().stream()
                .filter(r -> StringUtils.isNotBlank(r.getMessageId()))
                .forEach(r -> addReference(new Reference(r)));
        }
        this.originalInformation = message.getOriginalInformation();
        if (message.getDescs() != null) {
            message.getDescs().forEach(desc -> addDesc(new MessageDesc(desc)));
            message.getDescs().removeIf(d -> !d.descDefined());
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
            this.revision = sysMessage.getRevision();
            this.thumbnailPath = sysMessage.getThumbnailPath();
            this.separatePage = sysMessage.getSeparatePage();
            if (sysMessage.getPromulgations() != null) {
                sysMessage.getPromulgations().stream()
                    .filter(BaseMessagePromulgationVo::promulgationDataDefined)
                    .forEach(p -> addPromulgation(p.toEntity()));
            }
        }

        updateAggregateEventDateInterval();
    }


    /** Converts this entity to a value object */
    public <M extends MessageVo> M toVo(Class<M> clz, DataFilter filter) {

        M message = newInstance(clz);
        DataFilter compFilter = filter.forComponent(Message.class);

        message.setId(uid);
        message.setNumber(number);
        message.setShortId(shortId);
        message.setStatus(status);
        message.setMainType(mainType);
        message.setType(type);

        if (compFilter.includeDetails()) {
            message.setCreated(getCreated());
            message.setUpdated(getUpdated());
            if (messageSeries != null) {
                message.setMessageSeries(messageSeries.toVo(MessageSeriesVo.class, filter));
            }
            areas.forEach(a -> message.checkCreateAreas().add(a.toVo(AreaVo.class, filter)));
            categories.forEach(c -> message.checkCreateCategories().add(c.toVo(CategoryVo.class, filter)));
            charts.forEach(c -> message.checkCreateCharts().add(c.toVo(ChartVo.class, filter)));
            message.setHorizontalDatum(horizontalDatum);
            message.setPublishDateFrom(publishDateFrom);
            message.setPublishDateTo(publishDateTo);
            message.setFollowUpDate(followUpDate);
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

        if (message instanceof SystemMessageVo) {
            SystemMessageVo systemMessage = (SystemMessageVo)message;
            systemMessage.setRevision(revision + 1); // NB: Increase revision number
            systemMessage.setRepoPath(repoPath);
            systemMessage.setThumbnailPath(thumbnailPath);
            systemMessage.setAutoTitle(autoTitle);
            systemMessage.setSeparatePage(separatePage);

            // Check if we need to include promulgations
            if (!promulgations.isEmpty() && compFilter.includeField("promulgations")) {
                systemMessage.setPromulgations(promulgations.stream()
                    .map(BaseMessagePromulgation::toVo)
                    .collect(Collectors.toList()));
            }

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

        if (createdBy != null && lastUpdatedBy == null) {
            lastUpdatedBy = createdBy;
        }

        if (uid == null) {
            assignNewUid();
        }
        repoPath = UidUtils.uidToHashedFolderPath(MESSAGE_REPO_FOLDER, uid);

        // Set "area" to be the first area in the "areas" list
        area = areas.isEmpty() ? null : areas.get(0);

        // Update the main type from the type
        if (type != null) {
            mainType = type.getMainType();
        }

        // Remove blank references
        references.removeIf(r -> StringUtils.isBlank(r.getMessageId()));

        // Updates the start and end dates from the date intervals
        updateAggregateEventDateInterval();

        // Sync the year field from the publishDateFrom date
        year = publishDateFrom != null ? TimeUtils.getCalendarField(publishDateFrom, Calendar.YEAR) : null;

        // Update the auto-generated message fields
        updateAutoMessageFields();

        // Update the has-geometry flag
        hasGeometry = parts.stream().anyMatch(p -> p.getGeometry() != null);
    }


    /** Compute the total event date interval **/
    public void updateAggregateEventDateInterval() {
        // Update event date intervals
        eventDateFrom = eventDateTo = null;

        parts.stream()
                .flatMap(p -> p.getEventDates().stream())
                .forEach(di -> {
            if (eventDateFrom == null || (di.getFromDate() != null && eventDateFrom.after(di.getFromDate()))) {
                eventDateFrom = di.getFromDate();
            }
            if (eventDateTo == null || (di.getToDate() != null && eventDateTo.before(di.getToDate()))) {
                eventDateTo = di.getToDate();
            }
        });
    }


    /**
     * When a message is being published, check for the presence of event date intervals.
     * If none are present, add an event date interval to all "Details" message parts
     * with a start event date set to the publish start date.
     */
    public void checkEventDateIntervalsUponPublishStart() {
        if (publishDateFrom != null && parts.stream().allMatch(p -> p.getEventDates().isEmpty())) {
            parts.stream()
                    .filter(p -> p.getType() == MessagePartType.DETAILS)
                    .forEach(p -> p.addEventDates(new DateInterval(false, publishDateFrom, null)));
        }
    }


    /**
     * When a message is being expired or cancelled, check for the presence of open-ended event date intervals.
     * if such a date interval exists and the event start date is prior to the publish end date, then set the
     * event end date to that of the publish end date.
     * If however, the event start date of an open-ended event date interval is after the publish end date,
     * delete the event date interval altogether.
     */
    public void checkEventDateIntervalsUponPublishEnd() {
        if (publishDateTo != null) {
            parts.stream()
                    .flatMap(p -> p.getEventDates().stream())
                    .filter(ed -> ed.openEnded() && !ed.getFromDate().after(publishDateTo))
                    .forEach(ed -> ed.setToDate(publishDateTo));

            parts.forEach(p ->
                    p.getEventDates().removeIf(ed -> ed.openEnded() && ed.getFromDate().after(publishDateTo)));

        }
    }


    /** Updates all auto-generated fields **/
    public void updateAutoMessageFields() {
        updateMessageTitle();
    }


    /** Updates the title line of the message based on area, vicinity and subject */
    public void updateMessageTitle() {
        if (autoTitle) {
            // Get all involved message and message part languages
            computeLanguages().forEach(lang -> {
                try {
                    // First add area lineage
                    StringBuilder title = new StringBuilder();
                    title.append(computeAreaTitle(lang));

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
                    MessageDesc desc = getDesc(lang);
                    if (desc != null || StringUtils.isNotBlank(title.toString())) {
                        checkCreateDesc(lang).setTitle(title.toString().trim());
                    }
                } catch (Exception ignored) {
                }
            });

        }
    }


    /**
     * Computes the area title, i.e. the area + vicinity
     * @param lang the language to compute the area title for
     * @return the area title
     */
    public String computeAreaTitle(String lang) {
        // First add area lineage
        StringBuilder areaTitle = new StringBuilder();
        if (!getAreas().isEmpty()) {
            areaTitle.append(Area.computeAreaTitlePrefix(getAreas(), lang));
        }

        // If defined, add vicinity
        MessageDesc desc = getDesc(lang);
        if (desc != null && StringUtils.isNotBlank(desc.getVicinity())) {
            areaTitle.append(" ").append(desc.getVicinity());
            if (!desc.getVicinity().endsWith(".")) {
                areaTitle.append(".");
            }
        }
        return areaTitle.toString();
    }


    /** Returns the set of languages used for this message **/
    public Set<String> computeLanguages() {
        return Stream.concat(
                getDescs().stream().map(DescEntity::getLang),
                getParts().stream().flatMap(p -> p.getDescs().stream()).map(DescEntity::getLang)
        ).collect(Collectors.toSet());
    }


    /**
     * Assigns a new UID to the Feature
     * @noinspection all
     **/
    public String assignNewUid() {
        String oldRepoPath = repoPath;

        uid = UidUtils.newUid();
        repoPath = UidUtils.uidToHashedFolderPath(MESSAGE_REPO_FOLDER, uid);

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
            getAttachments().forEach(att -> {
                if (StringUtils.isNotBlank(att.getPath())) {
                    att.setPath(att.getPath().replace(oldRepoPath, repoPath));
                }
            });
            if (StringUtils.isNotBlank(thumbnailPath)) {
                thumbnailPath = thumbnailPath.replace(oldRepoPath, repoPath);
            }
        }
        return uid;
    }


    /**
     * Returns the promulgation with the given type, or null if not found
     * @param type the promulgation type
     * @return the promulgation with the given type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <P extends BaseMessagePromulgation> P promulgation(Class<P> clz, String type) {
        return (P) promulgations.stream()
                .filter(p -> p.getType().getTypeId().equals(type) && clz.isAssignableFrom(p.getClass()))
                .findFirst()
                .orElse(null);
    }


    /**
     * Returns the promulgation with the given type, or null if not found
     * @return the promulgation with the given type, or null if not found
     */
    public BaseMessagePromulgation promulgation(String type) {
        return promulgations.stream()
                .filter(p -> p.getType().getTypeId().equals(type))
                .findFirst()
                .orElse(null);
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


    /** Adds a promulgation entity to this message */
    public BaseMessagePromulgation addPromulgation(BaseMessagePromulgation promulgation) {
        promulgation.setMessage(this);
        promulgations.add(promulgation);
        return promulgation;
    }


    /** Returns all contained GeoJSON geometries **/
    public FeatureCollectionVo[] toGeoJson() {
        return parts.stream()
                .filter(p -> p.getGeometry() != null)
                .filter(p -> !p.getGeometry().getFeatures().isEmpty())
                .map(p -> p.getGeometry().toGeoJson())
                .toArray(FeatureCollectionVo[]::new);
    }


    /** Merges all contained GeoJSON geometries into a single FeatureCollectionVo **/
    public FeatureCollectionVo toFeatureCollection() {
        FeatureCollectionVo fc = new FeatureCollectionVo();
        fc.setFeatures(Arrays.stream(toGeoJson())
                .flatMap(f -> Arrays.stream(f.getFeatures()))
                .toArray(FeatureVo[]::new));
        return fc;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public User getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(User lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
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

    public Date getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(Date followUpDate) {
        this.followUpDate = followUpDate;
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

    public List<MessageHistory> getHistory() {
        return history;
    }

    public void setHistory(List<MessageHistory> history) {
        this.history = history;
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

    public Boolean getSeparatePage() {
        return separatePage;
    }

    public void setSeparatePage(Boolean separatePage) {
        this.separatePage = separatePage;
    }

    public List<BaseMessagePromulgation> getPromulgations() {
        return promulgations;
    }

    public void setPromulgations(List<BaseMessagePromulgation> promulgations) {
        this.promulgations = promulgations;
    }
}
