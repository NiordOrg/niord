package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

/**
 * Per-language series text. The PATTERNS, not the values they produce.
 */
public class PublicationSeriesDescVo implements IJsonSerializable {

    private String lang;

    private String name;

    private String nameSuggestionPattern;

    private String fileNamePattern;

    private String messageReferenceFormat;

    private String linkPattern;

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

    public String getNameSuggestionPattern() {
        return nameSuggestionPattern;
    }

    public void setNameSuggestionPattern(String nameSuggestionPattern) {
        this.nameSuggestionPattern = nameSuggestionPattern;
    }

    public String getFileNamePattern() {
        return fileNamePattern;
    }

    public void setFileNamePattern(String fileNamePattern) {
        this.fileNamePattern = fileNamePattern;
    }

    public String getMessageReferenceFormat() {
        return messageReferenceFormat;
    }

    public void setMessageReferenceFormat(String messageReferenceFormat) {
        this.messageReferenceFormat = messageReferenceFormat;
    }

    public String getLinkPattern() {
        return linkPattern;
    }

    public void setLinkPattern(String linkPattern) {
        this.linkPattern = linkPattern;
    }

}
