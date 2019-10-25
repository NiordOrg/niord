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
package org.niord.model.geojson;

import io.swagger.annotations.ApiModel;
import org.niord.model.IJsonSerializable;

import javax.xml.bind.annotation.XmlType;
import java.util.Properties;

/**
 * Implementation of the Coordinate Reference System, as defined in
 * http://geojson.org/geojson-spec.html#coordinate-reference-system-objects
 */
@ApiModel(value = "Crs", description = "GeoJson Coordinate Reference System")
@XmlType(name = "crs")
public class CrsVo implements IJsonSerializable {

    private String type;

    private Properties properties = new Properties();

    /**
     * Gets type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets type.
     *
     * @param type the type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets properties.
     *
     * @return the properties
     */
    public Properties getProperties() {
		return properties;
	}

    /**
     * Sets properties.
     *
     * @param properties the properties
     */
    public void setProperties(Properties properties) {
		this.properties = properties;
	}

}
