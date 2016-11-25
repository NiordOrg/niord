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

import org.niord.core.domain.vo.DomainVo;
import org.niord.model.publication.PublicationVo;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines a publication that may be associated with a message.
 */
@SuppressWarnings("unused")
public class SystemPublicationVo extends PublicationVo {

    String publicationIdFormat;
    PublicationMainType mainType;
    SystemPublicationVo template;
    DomainVo domain;
    MessagePublication messagePublication;
    boolean languageSpecific;
    Map<String, Object> printSettings = new HashMap<>();
    Map<String, Object> reportParams = new HashMap<>();


    /*************************/
    /** Getters and Setters **/
    /*************************/

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
}
