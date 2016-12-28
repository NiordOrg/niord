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
import org.niord.core.message.MessageTag;
import org.niord.core.model.VersionedEntity;
import org.niord.core.publication.vo.MessagePublication;
import org.niord.core.publication.vo.PeriodicalType;
import org.niord.core.publication.vo.PublicationMainType;
import org.niord.core.publication.vo.PublicationStatus;
import org.niord.core.publication.vo.SystemPublicationVo;
import org.niord.core.util.UidUtils;
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
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines a publication that may be associated with a message.
 */
@Entity
@Table(indexes = {
        @Index(name = "publication_id_k", columnList="publicationId"),
        @Index(name = "publication_type_k", columnList="type"),
        @Index(name = "publication_main_type_k", columnList="mainType"),
        @Index(name = "publication_status_k", columnList="status")
})
@NamedQueries({
        @NamedQuery(name="Publication.findByPublicationId",
                query="SELECT p FROM Publication p where p.publicationId = :publicationId"),
        @NamedQuery(name="Publication.findByPublicationIds",
                query="SELECT p FROM Publication p where p.publicationId in (:publicationIds)"),
        @NamedQuery(name="Publication.findByIds",
                query="SELECT p FROM Publication p where p.id in (:ids)"),
        @NamedQuery(name="Publication.findByTemplateId",
                query="SELECT p FROM Publication p where p.template is not null and p.template.publicationId = :templateId"),
        @NamedQuery(name="Publication.findTagsByPublicationIds",
                query="SELECT DISTINCT p.messageTag.tagId FROM Publication p where p.publicationId in (:publicationIds) "
                     + " and p.type = 'MESSAGE_REPORT' and p.messageTag is not null"),
        @NamedQuery(name="Publication.findRecordingPublications",
                query="SELECT p FROM Publication p where p.status = 'RECORDING' "
                        + " and p.type = 'MESSAGE_REPORT' and p.messageTag is not null "
                        + " and (p.domain is null or :series member of p.domain.messageSeries)")
})
@SuppressWarnings("unused")
public class Publication extends VersionedEntity<Integer> implements ILocalizable<PublicationDesc> {

    public static final String PUBLICATION_REPO_FOLDER = "publications";
    public static final Integer DEFAULT_EDITION = 1;


    @Column(nullable = false, unique = true, length = 36)
    String publicationId;

    // Unlike Publication.version, which is used to control optimistic locking, the Publication.revision
    // attribute is used define the repo-path sub-folder used for attachments when a message is edited.
    int revision;

    @NotNull
    @Enumerated(EnumType.STRING)
    PublicationMainType mainType = PublicationMainType.PUBLICATION;

    @NotNull
    @Enumerated(EnumType.STRING)
    PublicationType type = PublicationType.LINK;

    @NotNull
    @Enumerated(EnumType.STRING)
    PublicationStatus status = PublicationStatus.DRAFT;

    @ManyToOne
    Publication template;

    @NotNull
    @ManyToOne
    PublicationCategory category;

    @ManyToOne
    Domain domain;

    @Enumerated(EnumType.STRING)
    PeriodicalType periodicalType;

    /**
     * Can be used to e.g. make the messageTagFormat unique if multiple editions area created.
     * Example messageTagFormat: "firing-areas-${year}-v${edition}"
     * This would allow for multiple Firing Area reports in the same calendar year, but with separate message tags.
     */
    Integer edition;

    /** Used by templates to define the name format of associated message tag **/
    String messageTagFormat;

    /**
     * May be used to define a filter for message that should be tagged when the publication is in recording status.
     * Example filter:
     * "(msg.type == Type.TEMPORARY_NOTICE || msg.type == Type.PRELIMINARY_NOTICE) && msg.status == Status.PUBLISHED"
     */
    String messageTagFilter;

    /** Used for report-based publications to associated a list of messages **/
    @ManyToOne
    MessageTag messageTag;

    @Temporal(TemporalType.TIMESTAMP)
    Date publishDateFrom;

    @Temporal(TemporalType.TIMESTAMP)
    Date publishDateTo;

    @Enumerated(EnumType.STRING)
    MessagePublication messagePublication = MessagePublication.NONE;

    boolean languageSpecific;

    String repoPath;

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
        setCreated(publication.getCreated());
        setUpdated(publication.getUpdated());
        this.type = publication.getType();
        this.category = publication.getCategory() != null ? new PublicationCategory(publication.getCategory()) : null;
        this.publishDateFrom = publication.getPublishDateFrom();
        this.publishDateTo = publication.getPublishDateTo();

        if (publication.getDescs() != null) {
            publication.getDescs().stream()
                    .filter(PublicationDescVo::descDefined)
                    .forEach(desc -> checkCreateDesc(desc.getLang()).update(desc));
        }

        if (publication instanceof SystemPublicationVo) {
            SystemPublicationVo sysPub = (SystemPublicationVo) publication;
            this.revision = sysPub.getRevision();
            this.status = sysPub.getStatus();
            this.mainType = sysPub.getMainType();
            this.template = sysPub.getTemplate() != null ? new Publication(sysPub.getTemplate()) : null;
            this.domain = sysPub.getDomain() != null ? new Domain(sysPub.getDomain()) : null;
            this.edition = sysPub.getEdition();
            this.messageTagFormat = sysPub.getMessageTagFormat();
            this.messageTagFilter = sysPub.getMessageTagFilter();
            this.messageTag = sysPub.getMessageTag() != null ? new MessageTag(sysPub.getMessageTag()) : null;
            this.periodicalType = sysPub.getPeriodicalType();
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
        setCreated(publication.getCreated());
        setUpdated(publication.getUpdated());
        this.revision = publication.getRevision();
        this.mainType = publication.getMainType();
        this.status = publication.getStatus();
        this.template = publication.getTemplate();
        this.type = publication.getType();
        this.category = publication.getCategory();
        this.domain = publication.getDomain();
        this.edition = publication.getEdition();
        this.messageTagFormat = publication.getMessageTagFormat();
        this.messageTagFilter = publication.getMessageTagFilter();
        this.messageTag = publication.getMessageTag();
        this.periodicalType = publication.getPeriodicalType();
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


    /** Converts this entity to a value object */
    public <P extends PublicationVo> P toVo(Class<P> clz, DataFilter dataFilter) {

        DataFilter compFilter = dataFilter.forComponent(Publication.class);

        P publication = newInstance(clz);
        publication.setPublicationId(publicationId);
        publication.setCreated(getCreated());
        publication.setUpdated(getUpdated());
        publication.setType(type);
        publication.setCategory(category != null ? category.toVo(dataFilter) : null);
        publication.setPublishDateFrom(publishDateFrom);
        publication.setPublishDateTo(publishDateTo);

        if (!descs.isEmpty()) {
            publication.setDescs(getDescs(compFilter).stream()
                    .map(PublicationDesc::toVo)
                    .filter(PublicationDescVo::descDefined)
                    .collect(Collectors.toList()));
        }

        if (publication instanceof SystemPublicationVo) {
            SystemPublicationVo sysPub = (SystemPublicationVo) publication;
            sysPub.setRevision(revision + 1); // NB: Increase revision number
            sysPub.setStatus(status);
            sysPub.setMainType(mainType);
            sysPub.setTemplate(template != null ? template.toVo(SystemPublicationVo.class, dataFilter) : null);
            sysPub.setDomain((domain != null) ? domain.toVo()  : null);
            sysPub.setEdition(edition);
            sysPub.setMessageTagFormat(messageTagFormat);
            sysPub.setMessageTagFilter(messageTagFilter);
            sysPub.setMessageTag(messageTag != null ? messageTag.toVo() : null);
            sysPub.setPeriodicalType(periodicalType);
            sysPub.setMessagePublication(messagePublication);
            sysPub.setLanguageSpecific(languageSpecific);
            sysPub.setRepoPath(repoPath);

            if (!printSettings.isEmpty()) {
                sysPub.setPrintSettings(new HashMap<>(printSettings));
            }
            if (!reportParams.isEmpty()) {
                sysPub.setReportParams(new HashMap<>(reportParams));
            }
        }

        return publication;
    }


    /** Updates this publication from its template */
    public void updateFromTemplate(PublicationTemplateUpdateCtx ctx) {
        if (template == null || !status.isDraft()) {
            return;
        }

        this.type = ctx.val(template.getType(), type);
        this.category = ctx.val(template.getCategory(), category);
        this.domain = ctx.val(template.getDomain(), domain);
        this.messageTagFormat = ctx.str(template.getMessageTagFormat(), messageTagFormat);
        this.messageTagFilter = ctx.str(template.getMessageTagFilter(), messageTagFilter);
        if (this.messageTag == null && StringUtils.isNotBlank(this.messageTagFormat)) {
            this.messageTag = ctx.findOrCreatePublicMessageTag(this.messageTagFormat);
        }
        this.periodicalType = ctx.val(template.getPeriodicalType(), periodicalType);
        this.messagePublication = ctx.val(template.getMessagePublication(), messagePublication);
        this.languageSpecific = ctx.val(template.isLanguageSpecific(), languageSpecific);
        if (!template.getPrintSettings().isEmpty()) {
            this.printSettings.clear();
            this.printSettings.putAll(template.getPrintSettings());
        }
        if (!template.getReportParams().isEmpty()) {
            this.reportParams.clear();
            template.getReportParams().entrySet().forEach(kv ->
                    this.reportParams.put(kv.getKey(), ctx.str(kv.getValue().toString(), "")));
        }
        template.getDescs().forEach(d -> {
            PublicationDesc pubDesc = checkCreateDesc(d.getLang());
            pubDesc.setTitle(ctx.str(d.getTitleFormat(), pubDesc.getTitle()));
            pubDesc.setLink(ctx.str(d.getLink(), pubDesc.getLink()));
            pubDesc.setFileName(ctx.str(d.getFileName(), pubDesc.getFileName()));
            pubDesc.setMessagePublicationFormat(ctx.str(d.getMessagePublicationFormat(), pubDesc.getMessagePublicationFormat()));
        });
    }


    /** Checks that the publication ID and repoPath are defined. Creates new ones if not **/
    public void checkPublicationId() {
        if (StringUtils.isBlank(publicationId)) {
            assignNewPublicationId();
        } else if (StringUtils.isBlank(repoPath)) {
            repoPath = UidUtils.uidToHashedFolderPath(PUBLICATION_REPO_FOLDER, publicationId);
        }
    }


    /** Assigns a new UID to the publication **/
    public String assignNewPublicationId() {
        String oldRepoPath = repoPath;

        publicationId = UidUtils.newUid();
        repoPath = UidUtils.uidToHashedFolderPath(PUBLICATION_REPO_FOLDER, publicationId);

        // Rewrite any links pointing to the old repository path
        if (StringUtils.isNotBlank(oldRepoPath)) {
            String prefix = "\"/rest/repo/file/";
            getDescs().forEach(desc -> {
                        if (desc.getLink() != null && desc.getLink().contains(prefix + oldRepoPath)) {
                            desc.setLink(desc.getLink().replace(prefix + oldRepoPath, prefix + repoPath));
                        }
                    });
        }
        return publicationId;
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

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public PublicationMainType getMainType() {
        return mainType;
    }

    public void setMainType(PublicationMainType mainType) {
        this.mainType = mainType;
    }

    public PublicationType getType() {
        return type;
    }

    public void setType(PublicationType type) {
        this.type = type;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationStatus status) {
        this.status = status;
    }

    public Publication getTemplate() {
        return template;
    }

    public void setTemplate(Publication template) {
        this.template = template;
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

    public PeriodicalType getPeriodicalType() {
        return periodicalType;
    }

    public void setPeriodicalType(PeriodicalType periodicalType) {
        this.periodicalType = periodicalType;
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

    public Integer getEdition() {
        return edition;
    }

    public void setEdition(Integer edition) {
        this.edition = edition;
    }

    public String getMessageTagFormat() {
        return messageTagFormat;
    }

    public void setMessageTagFormat(String messageTagFormat) {
        this.messageTagFormat = messageTagFormat;
    }

    public String getMessageTagFilter() {
        return messageTagFilter;
    }

    public void setMessageTagFilter(String messageTagFilter) {
        this.messageTagFilter = messageTagFilter;
    }

    public MessageTag getMessageTag() {
        return messageTag;
    }

    public void setMessageTag(MessageTag messageTag) {
        this.messageTag = messageTag;
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

    @SuppressWarnings("all")
    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
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
