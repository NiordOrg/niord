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

import org.niord.core.message.vo.SystemMessageSeriesVo;
import org.niord.core.message.vo.SystemMessageSeriesVo.NumberSequenceType;
import org.niord.core.model.VersionedEntity;
import org.niord.model.DataFilter;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageSeriesVo;
import org.niord.model.message.Type;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
                query = "select ms FROM MessageSeries ms where lower(ms.seriesId) like lower(:term)")
})
@SuppressWarnings("unused")
public class MessageSeries extends VersionedEntity<Integer> {

    @Column(unique = true, nullable = false)
    String seriesId;

    @NotNull
    @Enumerated(EnumType.STRING)
    MainType mainType;

    String shortFormat;

    String navtexFormat;

    @NotNull
    NumberSequenceType numberSequenceType = NumberSequenceType.YEARLY;

    @ElementCollection(targetClass = Type.class)
    @Enumerated(EnumType.STRING)
    Set<Type> types = new HashSet<>();

    @ElementCollection
    List<String> editorFields = new ArrayList<>();


    /** Constructor */
    public MessageSeries() {
    }


    /** Constructor */
    public MessageSeries(String seriesId) {
        this.seriesId = seriesId;
    }


    /** Constructor */
    public MessageSeries(MessageSeriesVo series) {
        this.seriesId = series.getSeriesId();
        this.mainType = series.getMainType();

        if (series instanceof SystemMessageSeriesVo) {
            SystemMessageSeriesVo sysSeries = (SystemMessageSeriesVo)series;

            if (sysSeries.getTypes() != null) {
                this.types.addAll(sysSeries.getTypes());
            }
            this.shortFormat = sysSeries.getShortFormat();
            this.navtexFormat = sysSeries.getNavtexFormat();
            this.numberSequenceType = sysSeries.getNumberSequenceType() != null
                    ? sysSeries.getNumberSequenceType()
                    : NumberSequenceType.YEARLY;
            if (sysSeries.getEditorFields() != null) {
                editorFields.addAll(sysSeries.getEditorFields());
            }
        }
    }


    /** Converts this entity to a value object */
    public <M extends MessageSeriesVo> M toVo(Class<M> clz, DataFilter filter) {
        M series = newInstance(clz);

        series.setSeriesId(seriesId);

        DataFilter compFilter = filter.forComponent(MessageSeries.class);
        if (compFilter.includeDetails()) {
            series.setMainType(mainType);

            if (series instanceof SystemMessageSeriesVo) {
                SystemMessageSeriesVo sysSeries = (SystemMessageSeriesVo)series;
                if (!types.isEmpty()) {
                    sysSeries.setTypes(new HashSet<>(types));
                }
                sysSeries.setShortFormat(shortFormat);
                sysSeries.setNavtexFormat(navtexFormat);
                sysSeries.setNumberSequenceType(numberSequenceType);
                if (!editorFields.isEmpty()) {
                    sysSeries.setEditorFields(new ArrayList<>(editorFields));
                }
            }
        }
        return series;
    }


    /** Converts this entity to a value object */
    public <M extends MessageSeriesVo> M toVo(Class<M> clz) {
        return toVo(clz, DataFilter.get().fields(DataFilter.ALL));
    }


    /**
     * Return if the given type is valid for this message series
     * @param type the type to check
     * @return if the given type is valid for this message series
     */
    public boolean validType(Type type) {
        return validTypes().contains(type);
    }


    /** Returns the set of valid types for the message series **/
    public Set<Type> validTypes() {
        return types.isEmpty() ? mainType.getTypes() : types;
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

    public Set<Type> getTypes() {
        return types;
    }

    public void setTypes(Set<Type> types) {
        this.types = types;
    }

    public String getShortFormat() {
        return shortFormat;
    }

    public void setShortFormat(String shortFormat) {
        this.shortFormat = shortFormat;
    }

    public String getNavtexFormat() {
        return navtexFormat;
    }

    public void setNavtexFormat(String navtexFormat) {
        this.navtexFormat = navtexFormat;
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
