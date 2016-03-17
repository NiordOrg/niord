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
package org.niord.core.domain;

import org.niord.core.model.BaseEntity;
import org.niord.model.vo.DomainVo;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

/**
 * Represents an application domain
 */
@Entity
@Cacheable
@Table(indexes = {
        @Index(name = "domain_client_id", columnList="clientId", unique = true)
})
@NamedQueries({
        @NamedQuery(name="Domain.findByClientId",
                query="SELECT d FROM Domain d where d.clientId = :clientId")
})
public class Domain extends BaseEntity<Integer> {

    @NotNull
    String clientId;

    @NotNull
    String name;

    /** Constructor */
    public Domain() {
    }


    /** Constructor */
    public Domain(DomainVo domain) {
        this.id = domain.getId();
        this.clientId = domain.getClientId();
        this.name = domain.getName();
    }


    /** Converts this entity to a value object */
    public DomainVo toVo() {
        DomainVo domain = new DomainVo();
        domain.setId(id);
        domain.setClientId(clientId);
        domain.setName(name);
        return domain;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
