/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.message;

import org.niord.core.model.BaseEntity;
import org.niord.core.model.IndexedEntity;
import org.niord.model.ILocalizable;
import org.niord.model.vo.AttachmentVo;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a message attachment
 */
@Entity
@SuppressWarnings("unused")
public class Attachment extends BaseEntity<Integer> implements ILocalizable<AttachmentDesc>, IndexedEntity {

    @ManyToOne
    Message message;

    int indexNo;

    String type;

    String fileName;

    Date fileUpdated;

    long fileSize;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "entity", orphanRemoval = true)
    List<AttachmentDesc> descs = new ArrayList<>();


    /** Constructor */
    public Attachment() {
    }


    /** Constructor */
    public Attachment(AttachmentVo attachment) {

        this.id = attachment.getId();
        this.type = attachment.getType();
        this.fileName = attachment.getFileName();
        this.fileUpdated = attachment.getFileUpdated();
        this.fileSize = attachment.getFileSize() == null ? 0 : attachment.getFileSize();

        if (attachment.getDescs() != null) {
            attachment.getDescs().stream()
                    .forEach(desc -> createDesc(desc.getLang()).setCaption(desc.getCaption()));
        }
    }


    /** Converts this entity to a value object */
    public AttachmentVo toVo() {

        AttachmentVo attachment = new AttachmentVo();
        attachment.setId(id);
        attachment.setType(type);
        attachment.setFileName(fileName);
        attachment.setFileUpdated(fileUpdated);
        attachment.setFileSize(fileSize);

        if (!descs.isEmpty()) {
            attachment.setDescs(getDescs().stream()
                    .map(AttachmentDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return attachment;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public AttachmentDesc createDesc(String lang) {
        AttachmentDesc desc = new AttachmentDesc();
        desc.setLang(lang);
        desc.setEntity(this);
        getDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    @Override
    public int getIndexNo() {
        return indexNo;
    }

    @Override
    public void setIndexNo(int indexNo) {
        this.indexNo = indexNo;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Date getFileUpdated() {
        return fileUpdated;
    }

    public void setFileUpdated(Date fileUpdated) {
        this.fileUpdated = fileUpdated;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public List<AttachmentDesc> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<AttachmentDesc> descs) {
        this.descs = descs;
    }

}
