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
import org.niord.model.DataFilter;
import org.niord.model.ILocalizable;
import org.niord.model.message.AttachmentDescVo;
import org.niord.model.message.AttachmentVo;
import org.niord.model.message.AttachmentVo.AttachmentDisplayType;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    AttachmentDisplayType display;

    String width;

    String height;

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
        this.display = attachment.getDisplay();
        this.width = attachment.getWidth();
        this.height = attachment.getHeight();

        if (attachment.getDescs() != null) {
            attachment.getDescs().stream()
                    .filter(AttachmentDescVo::descDefined)
                    .forEach(desc -> createDesc(desc.getLang()).setCaption(desc.getCaption()));
        }
    }


    /** Converts this entity to a value object */
    public AttachmentVo toVo(DataFilter filter) {

        AttachmentVo attachment = new AttachmentVo();
        attachment.setId(id);
        attachment.setType(type);
        attachment.setFileName(fileName);
        attachment.setFileUpdated(fileUpdated);
        attachment.setFileSize(fileSize);
        attachment.setDisplay(display);
        attachment.setWidth(width);
        attachment.setHeight(height);

        if (!descs.isEmpty()) {
            attachment.setDescs(getDescs(filter).stream()
                    .map(AttachmentDesc::toVo)
                    .collect(Collectors.toList()));
        }
        return attachment;
    }


    /** Updates this attachment from another attachment */
    public void updateAttachment(Attachment attachment) {
        this.indexNo = attachment.getIndexNo();
        this.type = attachment.getType();
        this.fileName = attachment.getFileName();
        this.fileUpdated = attachment.getFileUpdated();
        this.fileSize = attachment.getFileSize();
        this.display = attachment.getDisplay();
        this.width = attachment.getWidth();
        this.height = attachment.getHeight();
        copyDescsAndRemoveBlanks(attachment.getDescs());
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

    public AttachmentDisplayType getDisplay() {
        return display;
    }

    public void setDisplay(AttachmentDisplayType display) {
        this.display = display;
    }

    public String getWidth() {
        return width;
    }

    public void setWidth(String width) {
        this.width = width;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
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
