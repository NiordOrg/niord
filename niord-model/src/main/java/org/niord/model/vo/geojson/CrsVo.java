package org.niord.model.vo.geojson;

import org.niord.model.IJsonSerializable;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the Coordinate Reference System, as defined in
 * http://geojson.org/geojson-spec.html#coordinate-reference-system-objects
 */
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
