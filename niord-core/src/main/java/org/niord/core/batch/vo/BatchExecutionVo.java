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

import jakarta.batch.runtime.BatchStatus;
import org.niord.model.IJsonSerializable;

import java.util.Date;

/**
 * Encapsulates a batch job execution
 **/
@SuppressWarnings("unused")
public class BatchExecutionVo implements IJsonSerializable {

    long executionId;
    BatchStatus batchStatus;
    Date startTime;
    Date endTime;
    boolean restartable;
    boolean stoppable;
    boolean abandonable;

    /** Update flags **/
    public void updateFlags() {
        stoppable = batchStatus == BatchStatus.STARTED || batchStatus == BatchStatus.STARTING;
        restartable = batchStatus == BatchStatus.FAILED || batchStatus == BatchStatus.STOPPED;
        abandonable = restartable;
    }

    /*************************/
    /** Getters and Setters **/
    /***/

    public long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(long executionId) {
        this.executionId = executionId;
    }

    public BatchStatus getBatchStatus() {
        return batchStatus;
    }

    public void setBatchStatus(BatchStatus batchStatus) {
        this.batchStatus = batchStatus;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public boolean isRestartable() {
        return restartable;
    }

    public void setRestartable(boolean restartable) {
        this.restartable = restartable;
    }

    public boolean isStoppable() {
        return stoppable;
    }

    public void setStoppable(boolean stoppable) {
        this.stoppable = stoppable;
    }

    public boolean isAbandonable() {
        return abandonable;
    }

    public void setAbandonable(boolean abandonable) {
        this.abandonable = abandonable;
    }
}