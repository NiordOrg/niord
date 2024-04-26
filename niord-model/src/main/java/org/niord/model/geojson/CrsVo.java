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

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.niord.model.IJsonSerializable;

import jakarta.xml.bind.annotation.XmlType;
import java.util.Properties;

/**
 * Implementation of the Coordinate Reference System, as defined in
 * http://geojson.org/geojson-spec.html#coordinate-reference-system-objects
 */
@Schema(
        name = "Crs",
        description = "GeoJson Coordinate Reference System"
)
@XmlType(name = "crs")
public class CrsVo implements IJsonSerializable {

    String type;
    Properties properties = new Properties();


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Properties getProperties() {
		return properties;
	}

    public void setProperties(Properties properties) {
		this.properties = properties;
	}

}
