package org.niord.core.batch.vo;

import org.niord.model.IJsonSerializable;

/**
 * Encapsulates the status of a named batch job
 */
@SuppressWarnings("unused")
public class BatchTypeVo implements IJsonSerializable {

    String name;
    int instanceCount;
    int runningExecutions;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(int instanceCount) {
        this.instanceCount = instanceCount;
    }

    public int getRunningExecutions() {
        return runningExecutions;
    }

    public void setRunningExecutions(int runningExecutions) {
        this.runningExecutions = runningExecutions;
    }
}
