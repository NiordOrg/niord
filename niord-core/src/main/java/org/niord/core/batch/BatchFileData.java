package org.niord.core.batch;

import javax.persistence.Entity;

/**
 * File path based batch data
 */
@Entity
@SuppressWarnings("unused")
public class BatchFileData extends BatchData {

    String batchFilePath;


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getBatchFilePath() {
        return batchFilePath;
    }

    public void setBatchFilePath(String batchFilePath) {
        this.batchFilePath = batchFilePath;
    }

}
