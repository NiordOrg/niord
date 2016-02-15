
package org.niord.model.vo;

import org.niord.model.IJsonSerializable;

/**
 * Represents a persisted message list filter
 */
public class MessageFilterVo implements IJsonSerializable {

    Long id;
    String name;
    String parameters;

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
}
