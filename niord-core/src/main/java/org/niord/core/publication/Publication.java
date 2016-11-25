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

import org.apache.commons.lang.StringUtils;
import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.domain.Domain;
import org.niord.core.model.BaseEntity;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.core.util.TimeUtils;
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.publication.PublicationDescVo;
import org.niord.model.publication.PublicationType;
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
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
                query="SELECT p FROM Publication p where p.publicationId = :publicationId"),
        @NamedQuery(name="Publication.findByTemplateId",
                query="SELECT p FROM Publication p where p.template is not null and p.template.publicationId = :templateId")
})
@SuppressWarnings("unused")
public class Publication extends BaseEntity<Integer> implements ILocalizable<PublicationDesc> {

    @NotNull
    String publicationId;

    /** Used by templates to define the format of the ID's of the concrete publications **/
    String publicationIdFormat;

    @NotNull
    @Enumerated(EnumType.STRING)
    PublicationMainType mainType = PublicationMainType.PUBLICATION;

    @NotNull
    @Enumerated(EnumType.STRING)
    PublicationType type = PublicationType.LINK;

    @ManyToOne
    Publication template;

    @NotNull
    @ManyToOne
    PublicationCategory category;

    @ManyToOne
    Domain domain;

    @Temporal(TemporalType.TIMESTAMP)
    Date publishDateFrom;

    @Temporal(TemporalType.TIMESTAMP)
    Date publishDateTo;

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
        this.category = publication.getCategory() != null ? new PublicationCategory(publication.getCategory()) : null;
        this.publishDateFrom = publication.getPublishDateFrom();
        this.publishDateTo = publication.getPublishDateTo();

        if (publication.getDescs() != null) {
            publication.getDescs().stream()
                    .filter(PublicationDescVo::descDefined)
                    .forEach(desc -> addDesc(new PublicationDesc(desc)));
        }

        if (publication instanceof SystemPublicationVo) {
            SystemPublicationVo sysPub = (SystemPublicationVo) publication;
            this.publicationIdFormat = sysPub.getPublicationIdFormat();
            this.mainType = sysPub.getMainType();
            this.template = sysPub.getTemplate() != null ? new Publication(sysPub.getTemplate()) : null;
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
        this.publicationIdFormat = publication.getPublicationIdFormat();
        this.mainType = publication.getMainType();
        this.template = publication.getTemplate();
        this.type = publication.getType();
        this.category = publication.getCategory();
        this.domain = publication.getDomain();
        this.publishDateFrom = publication.getPublishDateFrom();
        this.publishDateTo = publication.getPublishDateTo();
        this.messagePublication = publication.getMessagePublication();
        this.languageSpecific = publication.isLanguageSpecific();
        this.printSettings.clear();
        this.printSettings.putAll(publication.getPrintSettings());
        this.reportParams.clear();
        this.reportParams.putAll(publication.getReportParams());
        descs.clear();
        copyDescsAndRemoveBlanks(publication.getDescs());
    }


    /** Updates this publication from its template */
    public void updateFromTemplate() {
        if (template == null) {
            return;
        }

        if (StringUtils.isBlank(this.publicationId)) {
            this.publicationId = str(template.getPublicationIdFormat(), publicationId, publishDateFrom, null);
        }
        this.type = val(template.getType(), type);
        this.category = val(template.getCategory(), category);
        this.domain = val(template.getDomain(), domain);
        this.messagePublication = val(template.getMessagePublication(), messagePublication);
        this.languageSpecific = val(template.isLanguageSpecific(), languageSpecific);
        if (!template.getPrintSettings().isEmpty()) {
            this.printSettings.clear();
            this.printSettings.putAll(template.getPrintSettings());
        }
        if (!template.getReportParams().isEmpty()) {
            this.reportParams.clear();
            template.getReportParams().entrySet().forEach(kv ->
                    this.reportParams.put(kv.getKey(), str(kv.getValue().toString(), "", publishDateFrom, null)));
        }
        template.getDescs().forEach(d -> {
            PublicationDesc pubDesc = checkCreateDesc(d.getLang());
            pubDesc.setTitle(str(d.getTitle(), pubDesc.getTitle(), publishDateFrom, d.getLang()));
            pubDesc.setFormat(str(d.getFormat(), pubDesc.getFormat(), publishDateFrom, d.getLang()));
            pubDesc.setLink(str(d.getLink(), pubDesc.getLink(), publishDateFrom, d.getLang()));
        });
    }


    /** Converts this entity to a value object */
    public <P extends PublicationVo> P toVo(Class<P> clz, DataFilter dataFilter) {

        DataFilter compFilter = dataFilter.forComponent(Publication.class);

        P publication = newInstance(clz);
        publication.setPublicationId(publicationId);
        publication.setType(type);
        publication.setCategory(category.toVo(dataFilter));
        publication.setPublishDateFrom(publishDateFrom);
        publication.setPublishDateTo(publishDateTo);

        if (!descs.isEmpty()) {
            publication.setDescs(getDescs(compFilter).stream()
                    .map(PublicationDesc::toVo)
                    .collect(Collectors.toList()));
        }

        if (publication instanceof SystemPublicationVo) {
            SystemPublicationVo sysPub = (SystemPublicationVo) publication;
            sysPub.setPublicationIdFormat(publicationIdFormat);
            sysPub.setMainType(mainType);
            sysPub.setTemplate(template != null ? template.toVo(SystemPublicationVo.class, dataFilter) : null);
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


    /** Returns the value, or the default value if the value is null **/
    private <D> D val(D value, D defaultValue) {
        return value != null  ? value : defaultValue;
    }


    /**
     * Expand the parameters of a value with the format ${week}, ${year}, etc.
     * @param value the value to expand parameters for
     * @param date the date to use
     * @param lang the language to use
     * @return the updated value
     */
    private String str(String value, String defaultValue, Date date, String lang) {
        if (StringUtils.isNotBlank(value) && value.contains("${")) {
            Map<String, String> replacementValues = new HashMap<>();
            if (date != null) {
                int year = TimeUtils.getCalendarField(date, Calendar.YEAR);
                int week = TimeUtils.getCalendarField(date, Calendar.WEEK_OF_YEAR);

                replacementValues.put("${year-2-digits}", String.valueOf(year).substring(2));
                replacementValues.put("${year}", String.valueOf(year));
                replacementValues.put("${week}", String.valueOf(week));
                replacementValues.put("${week-2-digits}", String.format("%02d", week));
            }
            if (StringUtils.isNotBlank(lang)) {
                replacementValues.put("${lang}", lang);
            }
            for (Map.Entry<String, String> kv : replacementValues.entrySet()) {
                value = value.replace(kv.getKey(), kv.getValue());
            }
        } else if (StringUtils.isNotBlank(defaultValue)) {
            value = defaultValue;
        }
        return value;
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

    public String getPublicationIdFormat() {
        return publicationIdFormat;
    }

    public void setPublicationIdFormat(String publicationIdFormat) {
        this.publicationIdFormat = publicationIdFormat;
    }

    public PublicationMainType getMainType() {
        return mainType;
    }

    public void setMainType(PublicationMainType mainType) {
        this.mainType = mainType;
    }

    public Publication getTemplate() {
        return template;
    }

    public void setTemplate(Publication template) {
        this.template = template;
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
