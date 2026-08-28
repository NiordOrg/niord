package org.niord.core.publication.series;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.niord.core.model.DescEntity;
import org.niord.model.ILocalizedDesc;

/**
 * The series' per-language PATTERNS -- display name, name suggestion, file name, message
 * reference, link. Distinct from the issue desc, which holds the concrete values those patterns
 * produce.
 *
 * Identity comes from DescEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityContractTest.noEntityBringsItsOwnIdGenerator() enforces it.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "lang", "entity_id" }))
public class PublicationSeriesDesc extends DescEntity<PublicationSeries> {

    @Column(length = 255, nullable = false)
    private String name;

    @Column(length = 255)
    private String nameSuggestionPattern;

    @Column(length = 255)
    private String fileNamePattern;

    @Column(length = 512)
    private String messageReferenceFormat;

    @Column(length = 1024)
    private String linkPattern;

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

    @Override
    public boolean descDefined() {
        // D-7. The NAME decides, not "any field". A row carrying only a format string
        // and a blank name round-trips to nothing under the inherited rule, and the
        // citation text on it is lost without a word -- which is the legacy defect
        // this rule exists to close. Such a row is not defined, so it never persists.
        return ILocalizedDesc.fieldsDefined(name);
    }

    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationSeriesDesc desc = (PublicationSeriesDesc) localizedDesc;
        this.name = desc.getName();
        this.nameSuggestionPattern = desc.getNameSuggestionPattern();
        this.fileNamePattern = desc.getFileNamePattern();
        this.messageReferenceFormat = desc.getMessageReferenceFormat();
        this.linkPattern = desc.getLinkPattern();
    }

}
