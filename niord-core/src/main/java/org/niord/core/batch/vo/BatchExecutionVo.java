package org.niord.core.batch.vo;

import org.niord.model.IJsonSerializable;

import javax.batch.runtime.BatchStatus;
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
    /*************************/

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