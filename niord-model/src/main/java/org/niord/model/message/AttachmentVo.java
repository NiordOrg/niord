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

import io.swagger.annotations.ApiModel;
import org.niord.model.DataFilter;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * Value object for the {@code Attachment} model entity
 */
@ApiModel(value = "Attachment", description = "Message attachment model")
@XmlRootElement(name = "attachment")
@XmlType(propOrder = {
        "type", "fileName", "path", "fileSize", "display", "width", "height", "descs"
})
@SuppressWarnings("unused")
public class AttachmentVo implements ILocalizable<AttachmentDescVo>, IJsonSerializable {

    /**
     * The enum Attachment display type.
     */
    public enum AttachmentDisplayType {/**
     * Above attachment display type.
     */
    ABOVE,
        /**
         * Below attachment display type.
         */
        BELOW,
        /**
         * Separate page attachment display type.
         */
        SEPARATE_PAGE}

    /**
     * The Type.
     */
    String type;
    /**
     * The Path.
     */
    String path;
    /**
     * The File name.
     */
    String fileName;
    /**
     * The File size.
     */
    Long fileSize;
    /**
     * The Display.
     */
    AttachmentDisplayType display;
    /**
     * The Width.
     */
    String width;
    /**
     * The Height.
     */
    String height;
    /**
     * The Descs.
     */
    List<AttachmentDescVo> descs;


    /**
     * Returns a filtered copy of this entity  @param filter the filter
     *
     * @return the attachment vo
     */
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
     *
     * @param repoPath1 the repo path 1
     * @param repoPath2 the repo path 2
     */
    public void rewriteRepoPath(String repoPath1, String repoPath2) {
        if (path != null) {
            path = path.replace(repoPath1, repoPath2);
        }
    }

    /*************************/
    /** Getters and Setters **/
    /**
     * Gets type.
     *
     * @return the type
     */

    public String getType() {
        return type;
    }

    /**
     * Sets type.
     *
     * @param type the type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets path.
     *
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets path.
     *
     * @param path the path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Gets file name.
     *
     * @return the file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets file name.
     *
     * @param fileName the file name
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets file size.
     *
     * @return the file size
     */
    public Long getFileSize() {
        return fileSize;
    }

    /**
     * Sets file size.
     *
     * @param fileSize the file size
     */
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Gets display.
     *
     * @return the display
     */
    public AttachmentDisplayType getDisplay() {
        return display;
    }

    /**
     * Sets display.
     *
     * @param display the display
     */
    public void setDisplay(AttachmentDisplayType display) {
        this.display = display;
    }

    /**
     * Gets width.
     *
     * @return the width
     */
    public String getWidth() {
        return width;
    }

    /**
     * Sets width.
     *
     * @param width the width
     */
    public void setWidth(String width) {
        this.width = width;
    }

    /**
     * Gets height.
     *
     * @return the height
     */
    public String getHeight() {
        return height;
    }

    /**
     * Sets height.
     *
     * @param height the height
     */
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
