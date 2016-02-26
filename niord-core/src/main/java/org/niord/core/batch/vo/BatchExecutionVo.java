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