package org.niord.core.publication.series.vo;

import org.niord.core.publication.vo.MessagePublication;
import org.niord.model.IJsonSerializable;
import org.niord.model.publication.PublicationType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * An issue as a publication PICKER sees it, and nothing else.
 *
 * It exists because the two obvious alternatives are both wrong. PublicationVo
 * is a published XSD with a fixed propOrder and cannot gain a status field, and
 * the picker needs one -- a chip has to render a retired publication differently
 * from a current one. The system shape carries status and would also hand every
 * logged-in user the criteria document, the report configuration and the
 * repository path of every issue in the estate.
 *
 * So this is the minimum a picker and a citation chip actually read. Nothing
 * about how the document is produced, nothing about what it contains, no member
 * count. Keeping it separate is also what lets the admin list evolve without
 * touching the citation dialog.
 */
public class PublicationIssuePickerVo implements IJsonSerializable {

    private String publicId;

    private String publicationSeriesId;

    /**
     * The legacy vocabulary, DERIVED from the series' content mode.
     *
     * The shipped pickers filter on it -- one sends MESSAGE_REPORT, one sends
     * nothing -- so the value has to be the one they already understand. It is
     * computed rather than stored because the mapping from content mode is total
     * and bijective, and a second stored column can drift from the first while a
     * derivation cannot.
     */
    private PublicationType type;

    /** The field PublicationVo cannot carry, and the reason this type exists. */
    private String status;

    private MessagePublication messagePublication;

    /**
     * When this issue became the publicly current one -- the public window's
     * opening, never the content interval. An issue covering week 33 becomes
     * current when week 33 closes.
     */
    private Date publicFrom;

    /**
     * Whether the series publishes a separate document per language.
     *
     * Carried because the message editor's per-message link override is offered
     * only for a non-language-specific LINK publication that has no link of its
     * own, and this payload is the only thing that consumer reads. Without the
     * flag the rule is unevaluable and the override is either always offered or
     * never.
     */
    private boolean languageSpecific;

    private List<PublicationIssuePickerDescVo> descs = new ArrayList<>();

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getPublicationSeriesId() {
        return publicationSeriesId;
    }

    public void setPublicationSeriesId(String publicationSeriesId) {
        this.publicationSeriesId = publicationSeriesId;
    }

    public PublicationType getType() {
        return type;
    }

    public void setType(PublicationType type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public MessagePublication getMessagePublication() {
        return messagePublication;
    }

    public void setMessagePublication(MessagePublication messagePublication) {
        this.messagePublication = messagePublication;
    }

    public Date getPublicFrom() {
        return publicFrom;
    }

    public void setPublicFrom(Date publicFrom) {
        this.publicFrom = publicFrom;
    }

    public boolean isLanguageSpecific() {
        return languageSpecific;
    }

    public void setLanguageSpecific(boolean languageSpecific) {
        this.languageSpecific = languageSpecific;
    }

    public List<PublicationIssuePickerDescVo> getDescs() {
        return descs;
    }

    public void setDescs(List<PublicationIssuePickerDescVo> descs) {
        this.descs = descs;
    }
}
