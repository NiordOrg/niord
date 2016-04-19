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
package org.niord.model.vo.geojson;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlType;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the Coordinate Reference System, as defined in
 * http://geojson.org/geojson-spec.html#coordinate-reference-system-objects
 */
@ApiModel(value = "Crs", description = "GeoJson Coordinate Reference System")
@XmlType(name = "crs")
public class CrsVo implements IJsonSerializable {

    private String type;

    private Map<String, Object> properties = new HashMap<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getProperties() {
		return properties;
	}

    public void setProperties(Map<String, Object> properties) {
		this.properties = properties;
	}

}
