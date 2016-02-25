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
package org.niord.core.batch;

import org.niord.core.service.BaseService;
import org.slf4j.Logger;

import javax.batch.operations.JobOperator;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Provides an interface for managing batch jobs.
 * <p>
 * Note to self: Currently, there is a bug in Wildfly, so that the batch job xml files cannot be loaded
 * from an included jar.
 * Hence, move the xml files to META-INF/batch-jobs of the web application you are working on.
 *
 * @see <a hreg="https://issues.jboss.org/browse/WFLY-4988">Error report</a>
 * @see <a hreg="https://github.com/NiordOrg/niord-dk/tree/master/niord-dk-web">Example solution</a>
 */
@Stateless
public class BatchService extends BaseService {

    @Inject
    private Logger log;

    @Inject
    JobOperator jobOperator;

    /**
     * Starts a new batch job
     *
     * @param job the batch job
     */
    public long startBatchJob(BatchEntity job) {
        Objects.requireNonNull(job, "Invalid job parameter");
        Objects.requireNonNull(job.getName(), "Invalid job name");
        if (job.isPersisted()) {
            throw new IllegalArgumentException("Cannot start existing job");
        }

        // Launch the batch job
        Properties props = new Properties();
        props.put(IBatchable.BATCH_JOB_ENTITY, job);
        long executionId = jobOperator.start(job.getName(), props);

        log.info("Started batch job: " + job);
        return executionId;
    }


    /**
     * Creates or updates the batch job.
     *
     * @param job the batch job entity
     */
    public BatchEntity saveBatchJob(BatchEntity job) {
        System.out.println("Updating "  + job);
        job = saveEntity(job);
        em.flush();
        return job;
    }

    /**
     * Returns the batch job names
     * @return the batch job names
     */
    @SuppressWarnings("unchecked")
    public List<String> getJobNames() {
        // Sadly, this gets reset upon every JVM restart
        /*
        return jobOperator.getJobNames()
                .stream()
                .sorted()
                .collect(Collectors.toList());
        */

        // Look up the names from the database
        return (List<String>)em
                .createNativeQuery("select distinct JOBNAME from JOB_INSTANCE order by lower(JOBNAME)")
                .getResultList();
    }

}
