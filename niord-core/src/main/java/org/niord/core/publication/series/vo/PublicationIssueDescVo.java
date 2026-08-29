/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

/**
 * Per-language issue text: the CONCRETE values the series patterns produced.
 */
public class PublicationIssueDescVo implements IJsonSerializable {

    private String lang;

    private String name;

    private String fileName;

    private String link;

    private String messageReferenceFormat;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getMessageReferenceFormat() {
        return messageReferenceFormat;
    }

    public void setMessageReferenceFormat(String messageReferenceFormat) {
        this.messageReferenceFormat = messageReferenceFormat;
    }

}
