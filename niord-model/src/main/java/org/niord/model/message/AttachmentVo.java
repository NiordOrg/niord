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
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Date;
import java.util.List;

/**
 * Value object for the {@code Attachment} model entity
 */
@ApiModel(value = "Attachment", description = "Message attachment model")
@XmlRootElement(name = "attachment")
@XmlType(propOrder = {
        "type", "fileName", "fileUpdated", "fileSize", "display", "width", "height", "descs"
})
@SuppressWarnings("unused")
public class AttachmentVo implements ILocalizable<AttachmentDescVo>, IJsonSerializable {

    public enum AttachmentDisplayType { ABOVE, BELOW, SEPARATE_PAGE }

    String type;
    String fileName;
    Date fileUpdated;
    Long fileSize;
    AttachmentDisplayType display;
    String width;
    String height;
    List<AttachmentDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public AttachmentDescVo createDesc(String lang) {
        AttachmentDescVo desc = new AttachmentDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
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
