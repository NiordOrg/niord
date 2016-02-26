package org.niord.core.batch;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Transient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * File path based batch data
 */
@Entity
@SuppressWarnings("unused")
public class BatchRawData extends BatchData {

    @Lob
    byte[] data;

    boolean deflated;


    /**
     * If the data is deflated, this method will return the uncompressed data
     * @return the deflated data
     * @throws Exception
     */
    @Transient
    public <T> T getDeflatedData() throws Exception {
        if (deflated) {
            return readDeflatedData(data);
        }
        return (T)data;
    }

    /**
     * Sets and compresses the data
     * @param data the data to set
     */
    public void setDeflatedData(Object data) throws Exception {
        this.data = writeDeflatedData(data);
        deflated = true;
    }

    /** Returns a json representation of the data */
    public static <T> T readJsonData(Class<T> jsonClass, byte[] data) throws Exception {
        if (data == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(data, jsonClass);
    }

    /** Saves a json representation of the data */
    public static byte[] writeJsonData(Object jsonObject) throws Exception {
        if (jsonObject == null) {
            return null;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsBytes(jsonObject);
        }
    }

    /** Unzips the data */
    @SuppressWarnings("all")
    public static <T> T readDeflatedData(byte[] data) throws Exception {
        if (data == null) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ObjectInputStream objectIn = new ObjectInputStream(gzipIn)) {
            return (T) objectIn.readObject();
        }
    }

    /** Zips the data */
    public static byte[] writeDeflatedData(Object data) throws Exception {
        if (data == null) {
            return null;
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
                 ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut)) {
                objectOut.writeObject(data);
            }
            return baos.toByteArray();
        }
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isDeflated() {
        return deflated;
    }

    public void setDeflated(boolean deflated) {
        this.deflated = deflated;
    }
}
