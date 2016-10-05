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
package org.niord.core.message;

import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;
import org.niord.core.message.vo.MessageFilterVo;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

/**
 * Used for persisting a named message filter as defined by a request parameter string.
 */
@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = { "name", "user_id" })
)
@NamedQueries({
        @NamedQuery(name= "MessageFilter.findByUserAndIds",
                query="SELECT f FROM MessageFilter f where f.user = :user and f.id in (:ids)"),
        @NamedQuery(name= "MessageFilter.findByUserAndName",
                query="SELECT f FROM MessageFilter f where f.user = :user and f.name = :name"),
        @NamedQuery(name="MessageFilter.findByUser",
            query="SELECT f FROM MessageFilter f where f.user = :user")
})
@SuppressWarnings("unused")
public class MessageFilter extends BaseEntity<Integer> {

    @NotNull
    String name;

    String parameters;

    @ManyToOne
    User user;

    /**
     * Constructor
     */
    public MessageFilter() {
    }

    /**
     * Constructor
     */
    public MessageFilter(MessageFilterVo filter) {
        this.id = filter.getId();
        this.name = filter.getName();
        this.parameters = filter.getParameters();
    }


    /** Converts this entity to a value object */
    public MessageFilterVo toVo() {
        MessageFilterVo filter = new MessageFilterVo();
        filter.setId(id);
        filter.setName(name);
        filter.setParameters(parameters);
        return filter;
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

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
