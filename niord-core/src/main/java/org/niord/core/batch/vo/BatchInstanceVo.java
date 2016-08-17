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
package org.niord.core.batch.vo;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates a batch job instance.
 */
@SuppressWarnings("unused")
public class BatchInstanceVo implements IJsonSerializable {

    String name;
    long instanceId;
    List<BatchExecutionVo> executions = new ArrayList<>();

    // From the associated BatchData
    long jobNo;
    String fileName;
    String user;
    String domain;
    String jobName;
    Map<String, Object> properties;
    Integer progress;

    /**
     * Sorts the executions with the most recent execution first and update execution flags
     */
    public void updateExecutions() {
        // Sort executions
        Comparator<BatchExecutionVo> mostRecentFirst = (e1, e2) -> {
            if (e1.getStartTime() == null && e2.getStartTime() == null) {
                return 0;
            } else if (e1.getStartTime() == null) {
                return 1;
            } else if (e2.getStartTime() == null) {
                return -1;
            }
            return -e1.getStartTime().compareTo(e2.getStartTime());
        };
        executions.sort(mostRecentFirst);

        // Update execution flags
        executions.forEach(BatchExecutionVo::updateFlags);

        // Only latest execution of an instance can be restarted
        if (!executions.isEmpty()) {
            executions.stream()
                    .skip(1)
                    .forEach(e -> e.setRestartable(false));
        }

        // Execution can only be abandoned if there are no running executions for the instance
        if (executions.stream().anyMatch(BatchExecutionVo::isStoppable)) {
            executions.forEach(e -> e.setAbandonable(false));
        }
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(long instanceId) {
        this.instanceId = instanceId;
    }

    public long getJobNo() {
        return jobNo;
    }

    public void setJobNo(long jobNo) {
        this.jobNo = jobNo;
    }

    public List<BatchExecutionVo> getExecutions() {
        return executions;
    }

    public void setExecutions(List<BatchExecutionVo> executions) {
        this.executions = executions;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
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
