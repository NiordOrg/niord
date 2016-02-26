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

import org.slf4j.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Called when a batch job is started.
 *
 * Configure using:
 * <pre>
 *   &lt;batchlet ref="jobEndBatchlet" /&gt;
 * </pre>
 */
@Named
public class JobEndBatchlet extends AbstractBatchlet implements IBatchable {

    @Inject
    private Logger log;

    @Inject
    private JobContext jobContext;

    @Inject
    BatchService batchService;

    /** {@inheritDoc} **/
    @Override
    public String process() throws Exception {
        log.info("JobEndBatchlet started ");

        // Update the job entity with the execution id and batch status
        BatchEntity job = getBatchEntity(jobContext.getExecutionId());
        job.setStatus(BatchStatus.COMPLETED);
        batchService.saveBatchJob(job);

        return "COMPLETED";
    }
}
