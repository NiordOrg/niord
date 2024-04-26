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
package org.niord.core.batch;

import org.niord.core.db.JpaPropertiesAttributeConverter;
import org.niord.core.domain.Domain;
import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

    @ManyToOne
    Domain domain;

    String dataFileName;

    @Column(name="properties", columnDefinition = "TEXT")
    @Convert(converter = JpaPropertiesAttributeConverter.class)
    Map<String, Object> properties = new HashMap<>();

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
                ", domain=" + domain +
                ", dataFileName='" + dataFileName + '\'' +
                '}';
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

    public Domain getDomain() {
        return domain;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public String getDataFileName() {
        return dataFileName;
    }

    public void setDataFileName(String fileName) {
        this.dataFileName = fileName;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }
}
