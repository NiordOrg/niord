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
package org.niord.core.batch.vo;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Encapsulates a batch job instance
 */
@SuppressWarnings("unused")
public class BatchInstanceVo implements IJsonSerializable {

    String name;
    long instanceId;
    List<BatchExecutionVo> executions = new ArrayList<>();

    // From the associated BatchData
    String fileName;
    String fileType;
    String user;
    String jobName;

    /**
     * Sorts the executions with the most recent execution first and update execution flags
     **/
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

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
}
