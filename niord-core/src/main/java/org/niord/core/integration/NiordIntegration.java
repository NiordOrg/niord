/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.integration;


import org.niord.core.integration.vo.MessageSeriesMappingVo;
import org.niord.core.integration.vo.NiordIntegrationVo;
import org.niord.core.model.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines a Niord integration point, that is, the URL of another Niord server and a list of
 * message series mappings and import settings.
 * <p>
 * The import will only include public messages.
 */
@Entity
@NamedQueries({
        @NamedQuery(name="NiordIntegration.findPendingNiordIntegrations",
                query="SELECT i FROM NiordIntegration i where i.active = true and " +
                      " (i.nextScheduledExecution is null or i.nextScheduledExecution < current_timestamp) " +
                      " order by i.nextScheduledExecution desc")
})
@SuppressWarnings("unused")
public class NiordIntegration extends BaseEntity<Integer> {

    @NotNull
    String url;

    boolean active = true;

    boolean assignNewUids;

    boolean createBaseData;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "niordIntegration", orphanRemoval = true)
    List<MessageSeriesMapping> messageSeriesMappings = new ArrayList<>();

    // Defines the next computed execution time
    @Temporal(TemporalType.TIMESTAMP)
    Date nextScheduledExecution;


    /** No-argument constructor */
    public NiordIntegration() {
    }


    /**
     * Constructor
     * @param integration the Niord integration value object
     */
    public NiordIntegration(NiordIntegrationVo integration) {
        this.setId(integration.getId());
        this.url = integration.getUrl();
        this.active = integration.isActive();
        this.assignNewUids = integration.isAssignNewUids();
        this.createBaseData = integration.isCreateBaseData();
        integration.getMessageSeriesMappings().stream()
                .filter(MessageSeriesMappingVo::mappingDefined)
                .forEach(m -> addMessageSeriesMapping(new MessageSeriesMapping(m)));
        this.nextScheduledExecution = integration.getNextScheduledExecution();
    }


    /** Converts this entity to a value object */
    public NiordIntegrationVo toVo() {
        NiordIntegrationVo integration = new NiordIntegrationVo();
        integration.setId(this.getId());
        integration.setUrl(url);
        integration.setActive(active);
        integration.setAssignNewUids(assignNewUids);
        integration.setCreateBaseData(createBaseData);
        integration.setMessageSeriesMappings(
                messageSeriesMappings.stream()
                        .map(MessageSeriesMapping::toVo)
                        .collect(Collectors.toList()));
        integration.setNextScheduledExecution(nextScheduledExecution);
        return integration;
    }


    /**
     * Adds a new message series mapping to the Niord integration
     * @param mapping the mapping to add
     */
    public void addMessageSeriesMapping(MessageSeriesMapping mapping) {
        messageSeriesMappings.add(mapping);
        mapping.setNiordIntegration(this);
    }


    /*************************/
    /** Getters and Setters **/
    /***/

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAssignNewUids() {
        return assignNewUids;
    }

    public void setAssignNewUids(boolean assignNewUids) {
        this.assignNewUids = assignNewUids;
    }

    public boolean isCreateBaseData() {
        return createBaseData;
    }

    public void setCreateBaseData(boolean createBaseData) {
        this.createBaseData = createBaseData;
    }

    public List<MessageSeriesMapping> getMessageSeriesMappings() {
        return messageSeriesMappings;
    }

    public void setMessageSeriesMappings(List<MessageSeriesMapping> messageSeriesMappings) {
        this.messageSeriesMappings = messageSeriesMappings;
    }

    public Date getNextScheduledExecution() {
        return nextScheduledExecution;
    }

    public void setNextScheduledExecution(Date nextScheduledExecution) {
        this.nextScheduledExecution = nextScheduledExecution;
    }
}
