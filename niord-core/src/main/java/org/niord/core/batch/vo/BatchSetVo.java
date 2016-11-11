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

import java.util.Map;

/**
 * A <i>batch set</i> is a folder or a zipped archive that contains the following files:
 * <ul>
 *     <li>batch-set.json: Contains an array of BatchSetVo instances, each representing
 *              a batch job</li>
 *     <li>The batch job data files referenced in the batch-set.json file</li>
 * </ul>
 * <p>
 * A batch set can either be uploaded from the Admin -> Batch Jobs page or
 * via a "niord.batch-set" System setting.
 */
@SuppressWarnings("unused")
public class BatchSetVo implements IJsonSerializable {

    String jobName;
    String fileName;
    Map<String, Object> properties;
    long delay;

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }
}
