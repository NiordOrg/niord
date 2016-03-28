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

import org.niord.core.area.Area;
import org.niord.core.model.BaseEntity;
import org.niord.model.DataFilter;
import org.niord.model.vo.DomainVo;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
@SuppressWarnings("unused")
public class Domain extends BaseEntity<Integer> {

    @NotNull
    String clientId;

    @NotNull
    String name;

    @OneToMany
    List<Area> areas = new ArrayList<>();

    @Transient
    Boolean inKeycloak;

    /** Constructor */
    public Domain() {
    }


    /** Constructor */
    public Domain(DomainVo domain) {
        updateDomain(domain);
    }


    /** Updates this domain from the given domain */
    public void updateDomain(DomainVo domain) {
        this.clientId = domain.getClientId();
        this.name = domain.getName();
        if (domain.getAreas() != null) {
            this.areas = domain.getAreas().stream()
                .map(a -> new Area(a, DataFilter.get()))
                .collect(Collectors.toList());
        }
        this.inKeycloak = domain.getInKeycloak();
    }


    /** Converts this entity to a value object */
    public DomainVo toVo() {
        DomainVo domain = new DomainVo();
        domain.setClientId(clientId);
        domain.setName(name);
        domain.setInKeycloak(inKeycloak);
        if (!areas.isEmpty()) {
            domain.setAreas(areas.stream()
                .map(a -> a.toVo(DataFilter.get()))
                .collect(Collectors.toList()));
        }
        return domain;
    }


    /**
     * Checks if the values of the domain has changed.
     * Only checks relevant values, not e.g. database id, created date, etc.
     * Hence we do not use equals()
     *
     * @param template the template to compare with
     * @return if the domain has changed
     */
    @Transient
    public boolean hasChanged(Domain template) {
        return !Objects.equals(clientId, template.getClientId()) ||
                !Objects.equals(name, template.getName());
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

    public List<Area> getAreas() {
        return areas;
    }

    public void setAreas(List<Area> areas) {
        this.areas = areas;
    }

    public Boolean getInKeycloak() {
        return inKeycloak;
    }

    public void setInKeycloak(Boolean inKeycloak) {
        this.inKeycloak = inKeycloak;
    }
}
