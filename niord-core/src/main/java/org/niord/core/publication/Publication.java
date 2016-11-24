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

package org.niord.core.publication;

import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.domain.Domain;
import org.niord.core.model.BaseEntity;
import org.niord.model.publication.PublicationType;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.publication.PublicationDescVo;
import org.niord.model.publication.PublicationVo;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines a publication that may be associated with a message.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="Publication.findByPublicationId",
                query="SELECT p FROM Publication p where p.publicationId = :publicationId")
})
@SuppressWarnings("unused")
public class Publication extends BaseEntity<Integer> implements ILocalizable<PublicationDesc> {

    @NotNull
    String publicationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    PublicationType type = PublicationType.LINK;

    @NotNull
    @ManyToOne
    PublicationCategory category;

    @ManyToOne
    Domain domain;

    @NotNull
    @Enumerated(EnumType.STRING)
    MessagePublication messagePublication = MessagePublication.NONE;

    boolean languageSpecific;

    /** Selected report and print settings (page size, etc) **/
    @Column(name="printSettings", columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    Map<String, Object> printSettings = new HashMap<>();

    /** User input parameters for the selected report **/
    @Column(name="reportParams", columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    Map<String, Object> reportParams = new HashMap<>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<PublicationDesc> descs = new ArrayList<>();


    /** Constructor */
    public Publication() {
    }


    /** Constructor */
    public Publication(PublicationVo publication) {
        this.publicationId = publication.getPublicationId();
        this.type = publication.getType();
        this.category = new PublicationCategory(publication.getCategory());

        if (publication.getDescs() != null) {
            publication.getDescs().stream()
                    .filter(PublicationDescVo::descDefined)
                    .forEach(desc -> addDesc(new PublicationDesc(desc)));
        }

        if (publication instanceof SystemPublicationVo) {
            SystemPublicationVo sysPub = (SystemPublicationVo) publication;
            this.domain = sysPub.getDomain() != null ? new Domain(sysPub.getDomain()) : null;
            this.messagePublication = sysPub.getMessagePublication();
            this.languageSpecific = sysPub.isLanguageSpecific();
            if (sysPub.getPrintSettings() != null) {
                this.printSettings.putAll(sysPub.getPrintSettings());
            }
            if (sysPub.getReportParams() != null) {
                this.reportParams.putAll(sysPub.getReportParams());
            }
        }
    }


    /** Updates this publication from another publication */
    public void updatePublication(Publication publication) {
        this.publicationId = publication.getPublicationId();
        this.type = publication.getType();
        this.category = publication.getCategory();
        this.domain = publication.getDomain();
        this.messagePublication = publication.getMessagePublication();
        this.languageSpecific = publication.isLanguageSpecific();
        this.printSettings.clear();
        this.printSettings.putAll(publication.getPrintSettings());
        this.reportParams.clear();
        this.reportParams.putAll(publication.getReportParams());
        descs.clear();
        copyDescsAndRemoveBlanks(publication.getDescs());
    }


    /** Converts this entity to a value object */
    public <P extends PublicationVo> P toVo(Class<P> clz, DataFilter dataFilter) {

        DataFilter compFilter = dataFilter.forComponent(Publication.class);

        P publication = newInstance(clz);
        publication.setPublicationId(publicationId);
        publication.setType(type);
        publication.setCategory(category.toVo(dataFilter));

        if (!descs.isEmpty()) {
            publication.setDescs(getDescs(compFilter).stream()
                    .map(PublicationDesc::toVo)
                    .collect(Collectors.toList()));
        }

        if (publication instanceof SystemPublicationVo) {
            SystemPublicationVo sysPub = (SystemPublicationVo) publication;
            sysPub.setDomain((domain != null) ? domain.toVo()  : null);
            sysPub.setMessagePublication(messagePublication);
            sysPub.setLanguageSpecific(languageSpecific);

            if (!printSettings.isEmpty()) {
                sysPub.setPrintSettings(new HashMap<>(printSettings));
            }
            if (!reportParams.isEmpty()) {
                sysPub.setReportParams(new HashMap<>(reportParams));
            }
        }

        return publication;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public PublicationDesc createDesc(String lang) {
        PublicationDesc desc = new PublicationDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }


    /** Adds a description entity to this entity */
    public void addDesc(PublicationDesc desc) {
        desc.setEntity(this);
        descs.add(desc);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    public PublicationType getType() {
        return type;
    }

    public void setType(PublicationType type) {
        this.type = type;
    }

    public PublicationCategory getCategory() {
        return category;
    }

    public void setCategory(PublicationCategory category) {
        this.category = category;
    }

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public MessagePublication getMessagePublication() {
        return messagePublication;
    }

    public void setMessagePublication(MessagePublication messagePublication) {
        this.messagePublication = messagePublication;
    }

    public boolean isLanguageSpecific() {
        return languageSpecific;
    }

    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    public Map<String, Object> getPrintSettings() {
        return printSettings;
    }

    public void setPrintSettings(Map<String, Object> printSettings) {
        this.printSettings = printSettings;
    }

    public Map<String, Object> getReportParams() {
        return reportParams;
    }

    public void setReportParams(Map<String, Object> reportParams) {
        this.reportParams = reportParams;
    }

    @Override
    public List<PublicationDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<PublicationDesc> descs) {
        this.descs = descs;
    }
}
