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

import io.quarkus.scheduler.Scheduled;
import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.Calendar;
import java.util.List;

/**
 * Business interface for managing the Niord integration points.
 * <p>
 * The service will periodically import public messages from other Niord serves as defined
 * by the {@code NiordIntegration} entity
 */
@RequestScoped
@SuppressWarnings("unused")
public class NiordIntegrationService extends BaseService {

    public static final int MINUTES_BETWEEN_EXECUTIONS = 60;

    @Inject
    private Logger log;

    @Inject
    NiordIntegrationExecutionService executionService;


    /**
     * Returns the Niord integration point with the given ID or null if not found
     * @param id the ID of the Niord integration point
     * @return the Niord integration point with the given ID or null if not found
     */
    public NiordIntegration findById(Integer id) {
        return getByPrimaryKey(NiordIntegration.class, id);
    }


    /**
     * Returns all Niord integration points
     * @return the list of all Niord integration points
     */
    public List<NiordIntegration> getAllNiordIntegrations() {
        return getAll(NiordIntegration.class);
    }


    /**
     * Returns pending active Niord integration points
     * @return the list of pending active Niord integration points
     */
    public List<NiordIntegration> getPendingNiordIntegrations() {
        return em.createNamedQuery("NiordIntegration.findPendingNiordIntegrations", NiordIntegration.class)
                .getResultList();
    }

    /**
     * Creates a new Niord integration point from the given template
     * @param integration the Niord integration point template
     * @return a new Niord integration point from the given template
     */
    @Transactional
    public NiordIntegration createNiordIntegration(NiordIntegration integration) {
        // Sanity checks
        if (integration == null || integration.isPersisted()) {
            throw new IllegalArgumentException("Cannot create existing Niord integration");
        }

        // Compute the next execution time
        computeNextScheduledExecution(integration);

        log.info("Creating new Niord Integration for server " + integration.getUrl());
        return saveEntity(integration);
    }


    /**
     * Updates an existing Niord integration point from the given template
     * @param integration the Niord integration point template
     * @return the persisted Niord integration
     */
    @Transactional
    public NiordIntegration updateNiordIntegration(NiordIntegration integration) {
        NiordIntegration original = findById(integration.getId());
        if (original == null) {
            throw new IllegalArgumentException("Cannot update non-existing Niord integration with ID "
                    + integration.getId());
        }

        original.setUrl(integration.getUrl());
        original.setActive(integration.isActive());
        original.setAssignNewUids(integration.isAssignNewUids());
        original.setCreateBaseData(integration.isCreateBaseData());
        original.getMessageSeriesMappings().clear();
        integration.getMessageSeriesMappings().forEach(original::addMessageSeriesMapping);

        // NB: We do not update the nextScheduledExecution attribute, as this gets computed by the system

        log.info("Updating Niord integration " + integration.getId());
        return saveEntity(original);
    }


    /**
     * Deletes the Niord integration point with the given ID
     * @param id the Niord integration point to delete
     * @return if the message was deleted
     * @noinspection all
     */
    @Transactional
    public boolean deleteNiordIntegration(Integer id) {

        NiordIntegration original = findById(id);
        if (original != null) {
            log.info("Removing Niord integration point " + id);
            remove(original);
            return true;
        }
        return false;
    }


    /**
     * Computes the next scheduled execution of the given NiordIntegration
     * @param integration the Niord integration point
     */
    private void computeNextScheduledExecution(NiordIntegration integration) {
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, MINUTES_BETWEEN_EXECUTIONS);
        // Add up to a minute random time
        now.add(Calendar.SECOND, (int)(Math.random() * 60.0));
        integration.setNextScheduledExecution(now.getTime());
    }


    /**
     * Called every minute and processes the next pending Niord Integration
     */
    @Scheduled(cron="51 */1 * * * ?")
    void processNextPendingNiordIntegration() {
        List<NiordIntegration> integrations = getPendingNiordIntegrations();
        if (!integrations.isEmpty()) {
            processNiordIntegration(integrations.get(0).getId());
        }
    }


    /**
     * Processes the given Niord Integration
     */
    @Transactional
    public void processNiordIntegration(Integer id) {

        NiordIntegration integration = findById(id);
        if (integration == null) {
            log.error("No Niord Integration with ID " + id);
            throw new IllegalArgumentException("No Niord Integration with ID " + id);
        }

        try {
            log.debug(String.format("Processing Niord Integration %d for server %s",
                    integration.getId(),
                    integration.getUrl()));
            executionService.processNiordIntegration(integration.toVo());
        } catch (Exception ex) {
            log.error("Error processing Niord Integration " + integration.getId(), ex);
        }

        // Update the next execution time
        computeNextScheduledExecution(integration);
        saveEntity(integration);
    }

}
