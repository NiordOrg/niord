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
package org.niord.core.batch;

import org.niord.core.model.BaseEntity;
import org.niord.core.model.User;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

/**
 * Used for storing batch data associated with a JSR-352 batch instance.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "batchDataType")
@NamedQueries({
        @NamedQuery(name  = "BatchData.findByInstanceId",
                query = "select bd from BatchData bd where bd.instanceId = :instanceId"),
        @NamedQuery(name  = "BatchData.findByInstanceIds",
                query = "select bd from BatchData bd where bd.instanceId in :instanceIds")
})
public abstract class BatchData extends BaseEntity<Integer> {

    @NotNull
    String jobName;

    @NotNull
    Long instanceId;

    @ManyToOne
    User user;

    String fileName;

    String fileType;

    @Override
    public String toString() {
        return "BatchData{" +
                "id=" + id +
                ", jobName='" + jobName + '\'' +
                ", instanceId=" + instanceId +
                ", user=" + user +
                ", batchFileName='" + fileName + '\'' +
                ", batchFileType='" + fileType + '\'' +
                '}';
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(Long instanceId) {
        this.instanceId = instanceId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

}
