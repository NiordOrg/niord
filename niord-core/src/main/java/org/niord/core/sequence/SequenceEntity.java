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
package org.niord.core.sequence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
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