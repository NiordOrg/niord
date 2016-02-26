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
