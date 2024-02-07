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
package org.niord.core.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Used internally by {@linkplain SequenceService} to
 * manage sequences.
 */
@Entity
@Table(name="Sequence",
    indexes = {
        @Index(name = "sequence_name", columnList="name", unique = true)
})
public class SequenceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    String name;

    long nextValue;


    /**
     * Returns and bumps the next value of this sequence
     * @return the next value
     */
    public long nextValue() {
        return nextValue++;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    @Id
    @Column(unique = true, nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }
}