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

import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageSeriesVo;
import org.niord.model.message.MessageSeriesVo.NumberSequenceType;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;


/**
 * Represents a message series
 */
@Entity
@Cacheable
@NamedQueries({
        @NamedQuery(name="MessageSeries.findBySeriesId",
                query="SELECT ms FROM MessageSeries ms where ms.seriesId = :seriesId"),
        @NamedQuery(name="MessageSeries.findBySeriesIds",
                query="SELECT ms FROM MessageSeries ms where ms.seriesId in (:seriesIds)"),
        @NamedQuery(name  = "MessageSeries.searchMessageSeries",
                query = "select ms FROM MessageSeries ms where lower(ms.mrnFormat) like lower(:term) "
                      + "or lower(ms.seriesId) like lower(:term)")
})
@SuppressWarnings("unused")
public class MessageSeries extends VersionedEntity<Integer> {

    @Column(unique = true, nullable = false)
    String seriesId;

    @NotNull
    @Enumerated(EnumType.STRING)
    MainType mainType;

    String mrnFormat;

    String shortFormat;

    @NotNull
    NumberSequenceType numberSequenceType = NumberSequenceType.YEARLY;


    @ElementCollection
    List<String> editorFields = new ArrayList<>();

    /** Constructor */
    public MessageSeries() {
    }


    /** Constructor */
    public MessageSeries(MessageSeriesVo series) {
        this.seriesId = series.getSeriesId();
        this.mainType = series.getMainType();
        this.mrnFormat = series.getMrnFormat();
        this.shortFormat = series.getShortFormat();
        this.numberSequenceType = series.getNumberSequenceType() != null
                ? series.getNumberSequenceType()
                : NumberSequenceType.YEARLY;
        if (series.getEditorFields() != null) {
            editorFields.addAll(series.getEditorFields());
        }
    }


    /** Converts this entity to a value object */
    public MessageSeriesVo toVo(DataFilter filter) {
        MessageSeriesVo series = new MessageSeriesVo();
        series.setSeriesId(seriesId);

        DataFilter compFilter = filter.forComponent(MessageSeries.class);
        if (compFilter.includeDetails()) {
            series.setMainType(mainType);
            series.setMrnFormat(mrnFormat);
            series.setShortFormat(shortFormat);
            series.setNumberSequenceType(numberSequenceType);
            if (!editorFields.isEmpty()) {
                series.setEditorFields(new ArrayList<>(editorFields));
            }
        }
        return series;
    }

    /** Converts this entity to a value object */
    public MessageSeriesVo toVo() {
        return toVo(DataFilter.get().fields(DataFilter.ALL));
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public MainType getMainType() {
        return mainType;
    }

    public void setMainType(MainType mainType) {
        this.mainType = mainType;
    }

    public String getMrnFormat() {
        return mrnFormat;
    }

    public void setMrnFormat(String mrnFormat) {
        this.mrnFormat = mrnFormat;
    }

    public String getShortFormat() {
        return shortFormat;
    }

    public void setShortFormat(String shortFormat) {
        this.shortFormat = shortFormat;
    }

    public NumberSequenceType getNumberSequenceType() {
        return numberSequenceType;
    }

    public void setNumberSequenceType(NumberSequenceType numberSequenceType) {
        this.numberSequenceType = numberSequenceType;
    }

    public List<String> getEditorFields() {
        return editorFields;
    }

    public void setEditorFields(List<String> editorFields) {
        this.editorFields = editorFields;
    }
}
