package org.niord.core.publication.series.vo;

import org.niord.model.IJsonSerializable;

/**
 * One language of a picker row: what to show, where it points, how to cite it.
 *
 * Four fields, and each one is read by a shipped consumer. {@code title} is the
 * label in the list, {@code link} is the href a chip resolves to, and
 * {@code messagePublicationFormat} is the wording that gets written INTO a
 * message when the publication is cited -- without it the citation dialog can
 * offer a publication it cannot actually insert.
 */
public class PublicationIssuePickerDescVo implements IJsonSerializable {

    private String lang;

    /** The issue's name in this language. Called title because that is what a picker shows. */
    private String title;

    private String link;

    private String messagePublicationFormat;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getMessagePublicationFormat() {
        return messagePublicationFormat;
    }

    public void setMessagePublicationFormat(String messagePublicationFormat) {
        this.messagePublicationFormat = messagePublicationFormat;
    }
}
