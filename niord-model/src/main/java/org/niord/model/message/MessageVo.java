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

/**
 * Value object for the {@code Message} model entity
 */
@ApiModel(value = "Message", description = "Main NW and NM message class")
@XmlRootElement(name = "message")
@XmlType(propOrder = {
        "messageSeries", "number", "shortId", "mainType", "type", "status",
        "areas", "categories", "charts", "horizontalDatum", "publishDateFrom", "publishDateTo",
        "references", "originalInformation", "parts", "descs", "attachments"
})
@SuppressWarnings("unused")
public class MessageVo implements ILocalizable<MessageDescVo>, IJsonSerializable {

    String id;
    Date created;
    Date updated;
    MessageSeriesVo messageSeries;
    Integer number;
    String shortId;
    MainType mainType;
    Type type;
    Status status;
    List<AreaVo> areas;
    List<CategoryVo> categories;
    List<ChartVo> charts;
    String horizontalDatum;
    Date publishDateFrom;
    Date publishDateTo;
    List<ReferenceVo> references;
    Boolean originalInformation;
    List<MessagePartVo> parts;
    List<MessageDescVo> descs;
    List<AttachmentVo> attachments;


    /** Returns a filtered copy of this entity **/
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
     * Sort all entity-owned descriptor records by the given language
     * @param language the language to sort by
     */
    public void sort(String language) {
        if (StringUtils.isNotBlank(language)) {
            sortDescs(language);
            if (getAttachments() != null) {
                getAttachments().forEach(att -> att.sortDescs(language));
            }
            if (getReferences() != null) {
                getReferences().forEach(ref -> ref.sortDescs(language));
            }
            if (getParts() != null) {
                getParts().forEach(part -> part.sortDescs(language));
            }
        }
    }

    /**
     * Rewrites the attachments, thumbnail path and rich text description from one repository path to another.
     * This happens when a message is edited and its associated repository folder gets
     * copied to a temporary folder until the message is saved.
     * <p>
     * The repository paths may occur in e.g. embedded images and links.
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
    /*************************/

    public List<MessagePartVo> checkCreateParts() {
        if (parts == null) {
            parts = new ArrayList<>();
        }
        return parts;
    }

    public List<AreaVo> checkCreateAreas() {
        if (areas == null) {
            areas = new ArrayList<>();
        }
        return areas;
    }

    public List<CategoryVo> checkCreateCategories() {
        if (categories == null) {
            categories = new ArrayList<>();
        }
        return categories;
    }

    public List<ChartVo> checkCreateCharts() {
        if (charts == null) {
            charts = new ArrayList<>();
        }
        return charts;
    }

    public List<ReferenceVo> checkCreateReferences() {
        if (references == null) {
            references = new ArrayList<>();
        }
        return references;
    }

    public List<AttachmentVo> checkCreateAttachments() {
        if (attachments == null) {
            attachments = new ArrayList<>();
        }
        return attachments;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlAttribute
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @XmlAttribute
    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }
    public MessageSeriesVo getMessageSeries() {
        return messageSeries;
    }

    public void setMessageSeries(MessageSeriesVo messageSeries) {
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

    public List<AreaVo> getAreas() {
        return areas;
    }

    public void setAreas(List<AreaVo> areas) {
        this.areas = areas;
    }

    public List<CategoryVo> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryVo> categories) {
        this.categories = categories;
    }

    public List<ChartVo> getCharts() {
        return charts;
    }

    public void setCharts(List<ChartVo> charts) {
        this.charts = charts;
    }

    public String getHorizontalDatum() {
        return horizontalDatum;
    }

    public void setHorizontalDatum(String horizontalDatum) {
        this.horizontalDatum = horizontalDatum;
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

    public List<ReferenceVo> getReferences() {
        return references;
    }

    public void setReferences(List<ReferenceVo> references) {
        this.references = references;
    }

    public Boolean getOriginalInformation() {
        return originalInformation;
    }

    public void setOriginalInformation(Boolean originalInformation) {
        this.originalInformation = originalInformation;
    }

    public List<MessagePartVo> getParts() {
        return parts;
    }

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

    public List<AttachmentVo> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentVo> attachments) {
        this.attachments = attachments;
    }
}
