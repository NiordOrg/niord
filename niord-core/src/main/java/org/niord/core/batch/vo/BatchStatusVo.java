package org.niord.core.batch.vo;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the status of all batch job
 */
@SuppressWarnings("unused")
public class BatchStatusVo implements IJsonSerializable {

    int runningExecutions;
    List<BatchTypeVo> types = new ArrayList<>();

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public int getRunningExecutions() {
        return runningExecutions;
    }

    public void setRunningExecutions(int runningExecutions) {
        this.runningExecutions = runningExecutions;
    }

    public List<BatchTypeVo> getTypes() {
        return types;
    }

    public void setTypes(List<BatchTypeVo> types) {
        this.types = types;
    }
}
