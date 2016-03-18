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
import org.niord.core.user.User;
import org.niord.core.util.JsonUtils;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

/**
 * Used for storing batch data associated with a JSR-352 batch instance.
 */
@Entity
@Table(indexes = {
        @Index(name = "batch_data_instance_id", columnList="instanceId")
})
@NamedQueries({
        @NamedQuery(name  = "BatchData.findByInstanceId",
                query = "select bd from BatchData bd where bd.instanceId = :instanceId"),
        @NamedQuery(name  = "BatchData.findByInstanceIds",
                query = "select bd from BatchData bd where bd.instanceId in :instanceIds")
})
@SuppressWarnings("unused")
public class BatchData extends BaseEntity<Integer> {

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    Date created;

    @NotNull
    String jobName;

    @NotNull
    Long jobNo;

    @NotNull
    Long instanceId;

    @ManyToOne
    User user;

    String dataFileName;

    @Lob
    String properties;

    Integer progress;

    /** Ensures that the created data is set */
    @PrePersist
    protected void onCreate() {
        if (created == null) {
            created = new Date();
        }
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "BatchData{" +
                "id=" + id +
                ", jobName='" + jobName + '\'' +
                ", jobNo=" + jobNo +
                ", instanceId=" + instanceId +
                ", user=" + user +
                ", dataFileName='" + dataFileName + '\'' +
                '}';
    }


    /**
     * Writes the Properties as JSON
     * @param props the properties to write
     */
    public void writeProperties(Properties props) throws IOException {
        this.properties = JsonUtils.toJson(props);
    }


    /**
     * Reads the properties JSON as a Properties object
     */
    public Properties readProperties() throws IOException {
        return JsonUtils.fromJson(this.properties, Properties.class);
    }


    /**
     * Computes the path to the folder associated with the batch job. Returns null if no data file is defined.
     * The path is computed as:
     * <b>[jobName]/execution/[year]/[month]/[jobNo]</b>
     *
     * @return the path to the associated folder associated with the batch job.
     */
    public Path computeBatchJobFolderPath() {
        // Make sure the created data is instantiated
        if (created == null) {
            created = new Date();
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(created);

        return Paths.get(
                jobName,
                "execution",
                String.valueOf(cal.get(Calendar.YEAR)),
                String.valueOf(cal.get(Calendar.MONTH) + 1), // NB: month zero-based
                String.valueOf(jobNo));
    }

    /**
     * Computes the path to an associated data file. Returns null if no data file is defined.
     * The path is computed as:
     * <b>[jobName]/execution/[year]/[month]/[jobNo]/[dataFileName]</b>
     *
     * @return the path to the associated data file.
     */
    public Path computeDataFilePath() {
        // If no data file is defined, return null
        if (dataFileName == null) {
            return null;
        }

        return computeBatchJobFolderPath()
                .resolve(dataFileName);
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public Long getJobNo() {
        return jobNo;
    }

    public void setJobNo(Long jobNo) {
        this.jobNo = jobNo;
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

    public String getDataFileName() {
        return dataFileName;
    }

    public void setDataFileName(String fileName) {
        this.dataFileName = fileName;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }
}
