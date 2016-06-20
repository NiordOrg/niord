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
package org.niord.model.vo;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;
import org.niord.model.vo.geojson.FeatureCollectionVo;

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
        "messageSeries", "number", "mrn", "shortId", "mainType", "type", "status",
        "areas", "categories", "charts", "horizontalDatum", "geometry",
        "startDate", "endDate", "publishDate", "cancellationDate", "dateIntervals",
        "references", "atonUids", "originalInformation", "descs", "autoTitle"
})
@SuppressWarnings("unused")
public class MessageVo implements ILocalizable<MessageDescVo>, IJsonSerializable {

    Integer id;
    Date created;
    Date updated;
    Integer version;
    MessageSeriesVo messageSeries;
    Integer number;
    String mrn;
    String shortId;
    MainType mainType;
    Type type;
    Status status;
    List<AreaVo> areas;
    List<CategoryVo> categories;
    List<ChartVo> charts;
    String horizontalDatum;
    FeatureCollectionVo geometry;
    Date startDate;
    Date endDate;
    Date publishDate;
    Date cancellationDate;
    List<DateIntervalVo> dateIntervals;
    List<ReferenceVo> references;
    List<String> atonUids;
    Boolean originalInformation;
    List<MessageDescVo> descs;
    Boolean autoTitle;


    /** {@inheritDoc} */
    @Override
    public MessageDescVo createDesc(String lang) {
        MessageDescVo desc = new MessageDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Collection Handling **/
    /*************************/

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

    public List<DateIntervalVo> checkCreateDateIntervals() {
        if (dateIntervals == null) {
            dateIntervals = new ArrayList<>();
        }
        return dateIntervals;
    }

    public List<ReferenceVo> checkCreateReferences() {
        if (references == null) {
            references = new ArrayList<>();
        }
        return references;
    }

    public List<String> checkCreateAtonUids() {
        if (atonUids == null) {
            atonUids = new ArrayList<>();
        }
        return atonUids;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @XmlAttribute
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    @XmlAttribute
    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    public FeatureCollectionVo getGeometry() {
        return geometry;
    }

    public void setGeometry(FeatureCollectionVo geometry) {
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

    public List<DateIntervalVo> getDateIntervals() {
        return dateIntervals;
    }

    public void setDateIntervals(List<DateIntervalVo> dateIntervals) {
        this.dateIntervals = dateIntervals;
    }

    public List<ReferenceVo> getReferences() {
        return references;
    }

    public void setReferences(List<ReferenceVo> references) {
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
    public List<MessageDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MessageDescVo> descs) {
        this.descs = descs;
    }

    public Boolean isAutoTitle() {
        return autoTitle;
    }

    public void setAutoTitle(Boolean autoTitle) {
        this.autoTitle = autoTitle;
    }
}
