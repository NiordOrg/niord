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

package org.niord.core.publication.vo;

import org.apache.commons.lang.StringUtils;
import org.niord.core.domain.vo.DomainVo;
import org.niord.core.message.vo.MessageTagVo;
import org.niord.core.publication.Publication;
import org.niord.core.repo.IRepoBackedVo;
import org.niord.core.util.TimeUtils;
import org.niord.core.util.UidUtils;
import org.niord.model.publication.PublicationVo;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines a publication that may be associated with a message.
 */
@SuppressWarnings("unused")
public class SystemPublicationVo extends PublicationVo implements IRepoBackedVo {

    int revision;
    PublicationMainType mainType;
    PublicationStatus status;
    SystemPublicationVo template;
    DomainVo domain;
    PeriodicalType periodicalType;
    Integer edition;
    String messageTagFormat;
    String messageTagFilter;
    MessageTagVo messageTag;
    MessagePublication messagePublication;
    boolean languageSpecific;
    String repoPath;
    String editRepoPath;
    Map<String, Object> printSettings = new HashMap<>();
    Map<String, Object> reportParams = new HashMap<>();


    /** Assigns a new ID to the message **/
    public void assignNewId() {
        setPublicationId(UidUtils.newUid());
        repoPath = UidUtils.uidToHashedFolderPath(Publication.PUBLICATION_REPO_FOLDER, getPublicationId());
    }


    /** For periodicals, adjusts the publish date interval to the next issue **/
    public void nextIssue() {
        setPublishDateFrom(nextIssue(getPublishDateFrom(), getPeriodicalType()));
        setPublishDateTo(nextIssue(getPublishDateTo(), getPeriodicalType()));
    }


    /** Computes the next-issue date for the given periodical type **/
    private Date nextIssue(Date date, PeriodicalType type) {
        if (date != null && type != null) {
            switch (type) {
                case DAILY: date = TimeUtils.add(date, Calendar.DATE, 1); break;
                case WEEKLY: date = TimeUtils.add(date, Calendar.DATE, 7); break;
                case MONTHLY: date = TimeUtils.add(date, Calendar.MONTH, 1); break;
                case YEARLY: date = TimeUtils.add(date, Calendar.YEAR, 1); break;
            }
        }
        return date;
    }

    /**
     * Should be called before saving an edited message, to point links back to the publication repository
     */
    public void toPublicationRepo() {
        if (StringUtils.isNotBlank(getPublicationId()) && StringUtils.isBlank(repoPath)) {
            repoPath = UidUtils.uidToHashedFolderPath(Publication.PUBLICATION_REPO_FOLDER, getPublicationId());
        }
        rewriteRepoPath(getEditRepoPath(), getRepoPath());
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Override
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

    public PublicationStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationStatus status) {
        this.status = status;
    }

    public SystemPublicationVo getTemplate() {
        return template;
    }

    public void setTemplate(SystemPublicationVo template) {
        this.template = template;
    }

    public DomainVo getDomain() {
        return domain;
    }

    public void setDomain(DomainVo domain) {
        this.domain = domain;
    }

    public PeriodicalType getPeriodicalType() {
        return periodicalType;
    }

    public void setPeriodicalType(PeriodicalType periodicalType) {
        this.periodicalType = periodicalType;
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

    public MessageTagVo getMessageTag() {
        return messageTag;
    }

    public void setMessageTag(MessageTagVo messageTag) {
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

    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    @Override
    public String getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(String repoPath) {
        this.repoPath = repoPath;
    }

    @Override
    public String getEditRepoPath() {
        return editRepoPath;
    }

    @Override
    public void setEditRepoPath(String editRepoPath) {
        this.editRepoPath = editRepoPath;
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
}
