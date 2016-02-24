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
package org.niord.core.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.niord.core.model.VersionedEntity;

import javax.batch.runtime.BatchStatus;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Used for storing batch files.
 */
@Entity
@Table(name="batch")
public class BatchEntity extends VersionedEntity<Integer> {

    @NotNull
    String name;

    @Enumerated(EnumType.STRING)
    BatchStatus status;

    long executionId;

    @Basic(fetch = FetchType.LAZY)
    @Lob
    byte[] data;

    /** Returns a json representation of the data */
    public <T> T readJsonData(Class<T> jsonClass) throws Exception {
        if (data == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(data, jsonClass);
    }

    /** Saves a json representation of the data */
    public void writeJsonData(Object jsonObject) throws Exception {
        if (jsonObject == null) {
            this.data = null;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            data = mapper.writeValueAsBytes(jsonObject);
        }
    }

    /** Unzips the data */
    @SuppressWarnings("all")
    public <T> T readDeflatedData() throws Exception {
        if (data == null) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(this.data);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ObjectInputStream objectIn = new ObjectInputStream(gzipIn)) {
            return (T) objectIn.readObject();
        }
    }

    /** Zips the data */
    public void writeDeflatedData(Object data) throws Exception {
        if (data == null) {
            this.data = null;
        } else {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
                ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut)) {
                objectOut.writeObject(data);
            }
            this.data = baos.toByteArray();
        }
    }



    /** {@inheritDoc} **/
    @Override
    public String toString() {
        return "BatchEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", executionId=" + executionId +
                '}';
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

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(long executionId) {
        this.executionId = executionId;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}