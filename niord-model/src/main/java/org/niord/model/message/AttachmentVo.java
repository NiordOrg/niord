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
package org.niord.model.message;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import javax.xml.bind.annotation.XmlAttribute;
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

    Integer id;
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

    @XmlAttribute
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
