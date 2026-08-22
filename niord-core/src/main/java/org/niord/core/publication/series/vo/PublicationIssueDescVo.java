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
