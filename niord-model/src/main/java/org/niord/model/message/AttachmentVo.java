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
package org.niord.model.message;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Value object for the {@code Attachment} model entity
 */
@Schema(name = "Attachment", description = "Message attachment model")
@XmlRootElement(name = "attachment")
@XmlType(propOrder = {
        "type", "fileName", "path", "fileSize", "display", "width", "height", "descs"
})
@SuppressWarnings("unused")
public class AttachmentVo implements ILocalizable<AttachmentDescVo>, IJsonSerializable {

    public enum AttachmentDisplayType {ABOVE, BELOW, SEPARATE_PAGE}

    String type;
    String path;
    String fileName;
    Long fileSize;
    AttachmentDisplayType display;
    String width;
    String height;
    List<AttachmentDescVo> descs;


    /** Returns a filtered copy of this entity **/
    public AttachmentVo copy(DataFilter filter) {

        DataFilter compFilter = filter.forComponent("Attachment");

        AttachmentVo attachment = new AttachmentVo();
        attachment.setType(type);
        attachment.setPath(path);
        attachment.setFileName(fileName);
        attachment.setFileSize(fileSize);
        attachment.setDisplay(display);
        attachment.setWidth(width);
        attachment.setHeight(height);
        attachment.copyDescs(getDescs(compFilter));
        return attachment;
    }


    /** {@inheritDoc} */
    @Override
    public AttachmentDescVo createDesc(String lang) {
        AttachmentDescVo desc = new AttachmentDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    /**
     * Rewrites the attachments from one repository path to another.
     * This happens when a message is edited and its associated repository folder gets
     * copied to a temporary folder until the message is saved.
     */
    public void rewriteRepoPath(String repoPath1, String repoPath2) {
        if (path != null) {
            path = path.replace(repoPath1, repoPath2);
        }
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
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
    public List<AttachmentDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<AttachmentDescVo> descs) {
        this.descs = descs;
    }

}
