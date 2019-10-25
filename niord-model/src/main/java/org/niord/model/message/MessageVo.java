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
package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.apache.commons.lang.StringUtils;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Value object for the {@code Message} model entity
 */
@ApiModel(value = "Message", description = "Main NW and NM message class")
@XmlRootElement(name = "message")
@XmlType(propOrder = {
        "messageSeries", "number", "shortId", "mainType", "type", "status",
        "areas", "categories", "charts", "horizontalDatum", "publishDateFrom", "publishDateTo", "followUpDate",
        "references", "originalInformation", "parts", "descs", "attachments"
})
@SuppressWarnings("unused")
public class MessageVo implements ILocalizable<MessageDescVo>, IJsonSerializable {

    /**
     * The Id.
     */
    String id;
    /**
     * The Created.
     */
    Date created;
    /**
     * The Updated.
     */
    Date updated;
    /**
     * The Message series.
     */
    MessageSeriesVo messageSeries;
    /**
     * The Number.
     */
    Integer number;
    /**
     * The Short id.
     */
    String shortId;
    /**
     * The Main type.
     */
    MainType mainType;
    /**
     * The Type.
     */
    Type type;
    /**
     * The Status.
     */
    Status status;
    /**
     * The Areas.
     */
    List<AreaVo> areas;
    /**
     * The Categories.
     */
    List<CategoryVo> categories;
    /**
     * The Charts.
     */
    List<ChartVo> charts;
    /**
     * The Horizontal datum.
     */
    String horizontalDatum;
    /**
     * The Publish date from.
     */
    Date publishDateFrom;
    /**
     * The Publish date to.
     */
    Date publishDateTo;
    /**
     * The Follow up date.
     */
    Date followUpDate;
    /**
     * The References.
     */
    List<ReferenceVo> references;
    /**
     * The Original information.
     */
    Boolean originalInformation;
    /**
     * The Parts.
     */
    List<MessagePartVo> parts;
    /**
     * The Descs.
     */
    List<MessageDescVo> descs;
    /**
     * The Attachments.
     */
    List<AttachmentVo> attachments;


    /**
     * Returns a filtered copy of this entity  @param filter the filter
     *
     * @return the message vo
     */
    public MessageVo copy(DataFilter filter) {

        DataFilter compFilter = filter.forComponent("Message");

        MessageVo message = new MessageVo();
        message.setId(id);
        message.setNumber(number);
        message.setShortId(shortId);
        message.setStatus(status);
        message.setMainType(mainType);
        message.setType(type);

        if (compFilter.includeDetails()) {
            message.setCreated(created);
            message.setUpdated(updated);
            if (messageSeries != null) {
                message.setMessageSeries(messageSeries.copy(filter));
            }
            if (areas != null) {
                areas.forEach(a -> message.checkCreateAreas().add(a.copy(filter)));
            }
            if (categories != null) {
                categories.forEach(c -> message.checkCreateCategories().add(c.copy(filter)));
            }
            if (charts != null) {
                charts.forEach(c -> message.checkCreateCharts().add(c.copy(filter)));
            }
            message.setHorizontalDatum(horizontalDatum);
            message.setPublishDateFrom(publishDateFrom);
            message.setPublishDateTo(publishDateTo);
            message.setFollowUpDate(followUpDate);
            if (references != null) {
                references.forEach(r -> message.checkCreateReferences().add(r.copy(filter)));
            }
            message.setOriginalInformation(originalInformation);
            if (attachments != null) {
                attachments.forEach(att -> message.checkCreateAttachments().add(att.copy(filter)));
            }
        }
        if (compFilter.includeDetails() || compFilter.includeGeometry()) {
            parts.forEach(part -> message.checkCreateParts().add(part.copy(compFilter)));
        }
        if (compFilter.anyOfFields(DataFilter.DETAILS, "MessageDesc.title")) {
            message.copyDescs(getDescs(compFilter));
        }
        return message;
    }


    /** {@inheritDoc} */
    @Override
    public MessageDescVo createDesc(String lang) {
        MessageDescVo desc = new MessageDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /**
     * Returns the stream of localizable entities of the message  @return the stream
     */
    @SuppressWarnings("all")
    public Stream<ILocalizable> localizableStream() {
        List<ILocalizable> localizables = new ArrayList<>();
        localizables.add(this);
        if (attachments != null) {
            localizables.addAll(attachments);
        }
        if (references != null) {
            localizables.addAll(references);
        }
        if (parts != null) {
            localizables.addAll(parts);
        }
        if (areas != null) {
            areas.forEach(area -> {
                while (area != null) {
                    localizables.add(area);
                    area = area.getParent();
                }
            });
        }
        if (categories != null) {
            categories.forEach(category -> {
                while (category != null) {
                    localizables.add(category);
                    category = category.getParent();
                }
            });
        }
        return Stream.of(localizables.toArray(new ILocalizable[localizables.size()]));
    }


    /**
     * Sort all entity-owned descriptor records by the given language
     *
     * @param language the language to sort by
     */
    public void sort(String language) {
        if (StringUtils.isNotBlank(language)) {
            localizableStream().forEach(l -> l.sortDescs(language));
        }
    }


    /**
     * Rewrites the attachments, thumbnail path and rich text description from one repository path to another.
     * This happens when a message is edited and its associated repository folder gets
     * copied to a temporary folder until the message is saved.
     * <p>
     * The repository paths may occur in e.g. embedded images and links.
     *
     * @param repoPath1 the repo path 1
     * @param repoPath2 the repo path 2
     */
    public void rewriteRepoPath(String repoPath1, String repoPath2) {
        if (StringUtils.isNotBlank(repoPath1) && StringUtils.isNotBlank(repoPath2)) {
            if (getParts() != null) {
                getParts().forEach(mp -> mp.rewriteRepoPath(repoPath1, repoPath2));
            }
            if (getAttachments() != null) {
                getAttachments().forEach(att -> att.rewriteRepoPath(repoPath1, repoPath2));
            }
        }
    }


    /*************************/
    /** Collection Handling **/
    /**
     * Check create parts list.
     *
     * @return the list
     */

    public List<MessagePartVo> checkCreateParts() {
        if (parts == null) {
            parts = new ArrayList<>();
        }
        return parts;
    }

    /**
     * Check create areas list.
     *
     * @return the list
     */
    public List<AreaVo> checkCreateAreas() {
        if (areas == null) {
            areas = new ArrayList<>();
        }
        return areas;
    }

    /**
     * Check create categories list.
     *
     * @return the list
     */
    public List<CategoryVo> checkCreateCategories() {
        if (categories == null) {
            categories = new ArrayList<>();
        }
        return categories;
    }

    /**
     * Check create charts list.
     *
     * @return the list
     */
    public List<ChartVo> checkCreateCharts() {
        if (charts == null) {
            charts = new ArrayList<>();
        }
        return charts;
    }

    /**
     * Check create references list.
     *
     * @return the list
     */
    public List<ReferenceVo> checkCreateReferences() {
        if (references == null) {
            references = new ArrayList<>();
        }
        return references;
    }

    /**
     * Check create attachments list.
     *
     * @return the list
     */
    public List<AttachmentVo> checkCreateAttachments() {
        if (attachments == null) {
            attachments = new ArrayList<>();
        }
        return attachments;
    }

    /*************************/
    /** Getters and Setters **/
    /**
     * Gets id.
     *
     * @return the id
     */

    @XmlAttribute
    public String getId() {
        return id;
    }

    /**
     * Sets id.
     *
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets created.
     *
     * @return the created
     */
    @XmlAttribute
    public Date getCreated() {
        return created;
    }

    /**
     * Sets created.
     *
     * @param created the created
     */
    public void setCreated(Date created) {
        this.created = created;
    }

    /**
     * Gets updated.
     *
     * @return the updated
     */
    @XmlAttribute
    public Date getUpdated() {
        return updated;
    }

    /**
     * Sets updated.
     *
     * @param updated the updated
     */
    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    /**
     * Gets message series.
     *
     * @return the message series
     */
    public MessageSeriesVo getMessageSeries() {
        return messageSeries;
    }

    /**
     * Sets message series.
     *
     * @param messageSeries the message series
     */
    public void setMessageSeries(MessageSeriesVo messageSeries) {
        this.messageSeries = messageSeries;
    }

    /**
     * Gets number.
     *
     * @return the number
     */
    public Integer getNumber() {
        return number;
    }

    /**
     * Sets number.
     *
     * @param number the number
     */
    public void setNumber(Integer number) {
        this.number = number;
    }

    /**
     * Gets short id.
     *
     * @return the short id
     */
    public String getShortId() {
        return shortId;
    }

    /**
     * Sets short id.
     *
     * @param shortId the short id
     */
    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    /**
     * Gets main type.
     *
     * @return the main type
     */
    public MainType getMainType() {
        return mainType;
    }

    /**
     * Sets main type.
     *
     * @param mainType the main type
     */
    public void setMainType(MainType mainType) {
        this.mainType = mainType;
    }

    /**
     * Gets type.
     *
     * @return the type
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets type.
     *
     * @param type the type
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Gets status.
     *
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Sets status.
     *
     * @param status the status
     */
    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Gets areas.
     *
     * @return the areas
     */
    public List<AreaVo> getAreas() {
        return areas;
    }

    /**
     * Sets areas.
     *
     * @param areas the areas
     */
    public void setAreas(List<AreaVo> areas) {
        this.areas = areas;
    }

    /**
     * Gets categories.
     *
     * @return the categories
     */
    public List<CategoryVo> getCategories() {
        return categories;
    }

    /**
     * Sets categories.
     *
     * @param categories the categories
     */
    public void setCategories(List<CategoryVo> categories) {
        this.categories = categories;
    }

    /**
     * Gets charts.
     *
     * @return the charts
     */
    public List<ChartVo> getCharts() {
        return charts;
    }

    /**
     * Sets charts.
     *
     * @param charts the charts
     */
    public void setCharts(List<ChartVo> charts) {
        this.charts = charts;
    }

    /**
     * Gets horizontal datum.
     *
     * @return the horizontal datum
     */
    public String getHorizontalDatum() {
        return horizontalDatum;
    }

    /**
     * Sets horizontal datum.
     *
     * @param horizontalDatum the horizontal datum
     */
    public void setHorizontalDatum(String horizontalDatum) {
        this.horizontalDatum = horizontalDatum;
    }

    /**
     * Gets publish date from.
     *
     * @return the publish date from
     */
    public Date getPublishDateFrom() {
        return publishDateFrom;
    }

    /**
     * Sets publish date from.
     *
     * @param publishDateFrom the publish date from
     */
    public void setPublishDateFrom(Date publishDateFrom) {
        this.publishDateFrom = publishDateFrom;
    }

    /**
     * Gets publish date to.
     *
     * @return the publish date to
     */
    public Date getPublishDateTo() {
        return publishDateTo;
    }

    /**
     * Sets publish date to.
     *
     * @param publishDateTo the publish date to
     */
    public void setPublishDateTo(Date publishDateTo) {
        this.publishDateTo = publishDateTo;
    }

    /**
     * Gets follow up date.
     *
     * @return the follow up date
     */
    public Date getFollowUpDate() {
        return followUpDate;
    }

    /**
     * Sets follow up date.
     *
     * @param followUpDate the follow up date
     */
    public void setFollowUpDate(Date followUpDate) {
        this.followUpDate = followUpDate;
    }

    /**
     * Gets references.
     *
     * @return the references
     */
    public List<ReferenceVo> getReferences() {
        return references;
    }

    /**
     * Sets references.
     *
     * @param references the references
     */
    public void setReferences(List<ReferenceVo> references) {
        this.references = references;
    }

    /**
     * Gets original information.
     *
     * @return the original information
     */
    public Boolean getOriginalInformation() {
        return originalInformation;
    }

    /**
     * Sets original information.
     *
     * @param originalInformation the original information
     */
    public void setOriginalInformation(Boolean originalInformation) {
        this.originalInformation = originalInformation;
    }

    /**
     * Gets parts.
     *
     * @return the parts
     */
    public List<MessagePartVo> getParts() {
        return parts;
    }

    /**
     * Sets parts.
     *
     * @param parts the parts
     */
    public void setParts(List<MessagePartVo> parts) {
        this.parts = parts;
    }

    @Override
    public List<MessageDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessageDescVo> descs) {
        this.descs = descs;
    }

    /**
     * Gets attachments.
     *
     * @return the attachments
     */
    public List<AttachmentVo> getAttachments() {
        return attachments;
    }

    /**
     * Sets attachments.
     *
     * @param attachments the attachments
     */
    public void setAttachments(List<AttachmentVo> attachments) {
        this.attachments = attachments;
    }
}
