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
package org.niord.core.message;

import org.niord.core.model.BaseEntity;
import org.niord.core.user.User;
import org.niord.model.message.MessageFilterVo;

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
