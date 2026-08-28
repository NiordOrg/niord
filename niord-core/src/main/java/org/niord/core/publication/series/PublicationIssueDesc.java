/*
 * Copyright 2026 Danish Maritime Authority.
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

package org.niord.core.publication.series;

import jakarta.validation.constraints.NotNull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.UniqueConstraint;
import java.util.Date;
import org.niord.core.model.DescEntity;
import org.niord.core.user.User;
import org.niord.model.ILocalizedDesc;

/**
 * The issue's per-language CONCRETE VALUES -- the name, file name, path and link that the
 * series' patterns produced for this issue.
 *
 * Identity comes from DescEntity and nothing else. Every id in this
 * system is drawn from one shared sequence row, and inheriting the base class IS the whole
 * contract. Giving this table its own id generator would break that silently, for this
 * table alone. EntityContractTest.noEntityBringsItsOwnIdGenerator() enforces it.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "lang", "entity_id" }))
public class PublicationIssueDesc extends DescEntity<PublicationIssue> {

    @NotNull
    @Column(length = 255, nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean nameOverridden = false;

    @Column(length = 255)
    private String fileName;

    @Column(length = 512)
    private String filePath;

    @Column(length = 1024)
    private String link;

    @Column(length = 512)
    private String messageReferenceFormat;

    @Enumerated(EnumType.STRING)
    private FileSource fileSource;

    @Column(nullable = false)
    private boolean fileSourceSticky = false;

    @ManyToOne
    private User replacedBy;

    @Temporal(TemporalType.TIMESTAMP)
    private Date replacedAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date fileGeneratedAt;

    private Integer fileSize;

    @Column(length = 64)
    private String fileHash;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isNameOverridden() {
        return nameOverridden;
    }

    public void setNameOverridden(boolean nameOverridden) {
        this.nameOverridden = nameOverridden;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
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

    public FileSource getFileSource() {
        return fileSource;
    }

    public void setFileSource(FileSource fileSource) {
        this.fileSource = fileSource;
    }

    public boolean isFileSourceSticky() {
        return fileSourceSticky;
    }

    public void setFileSourceSticky(boolean fileSourceSticky) {
        this.fileSourceSticky = fileSourceSticky;
    }

    public User getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(User replacedBy) {
        this.replacedBy = replacedBy;
    }

    public Date getReplacedAt() {
        return replacedAt;
    }

    public void setReplacedAt(Date replacedAt) {
        this.replacedAt = replacedAt;
    }

    public Date getFileGeneratedAt() {
        return fileGeneratedAt;
    }

    public void setFileGeneratedAt(Date fileGeneratedAt) {
        this.fileGeneratedAt = fileGeneratedAt;
    }

    public Integer getFileSize() {
        return fileSize;
    }

    public void setFileSize(Integer fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    @Override
    public boolean descDefined() {
        // D-7, as on the series desc: the name decides. A file name or a link with
        // no name is a row that silently loses whatever else it carried.
        return ILocalizedDesc.fieldsDefined(name);
    }

    @Override
    public void copyDesc(ILocalizedDesc localizedDesc) {
        PublicationIssueDesc desc = (PublicationIssueDesc) localizedDesc;
        this.name = desc.getName();
        this.nameOverridden = desc.isNameOverridden();
        this.fileName = desc.getFileName();
        this.filePath = desc.getFilePath();
        this.link = desc.getLink();
        this.messageReferenceFormat = desc.getMessageReferenceFormat();
        this.fileSource = desc.getFileSource();
        this.fileSourceSticky = desc.isFileSourceSticky();
        this.replacedBy = desc.getReplacedBy();
        this.replacedAt = desc.getReplacedAt();
        this.fileGeneratedAt = desc.getFileGeneratedAt();
        this.fileSize = desc.getFileSize();
        this.fileHash = desc.getFileHash();
    }

}
