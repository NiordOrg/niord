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
