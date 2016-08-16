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
package org.niord.core.domain.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.util.JsonUtils;
import org.niord.model.message.DomainVo;

import javax.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads domains from a domains.json file.
 * <p>
 * Please note, the actual domain-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the DomainVo class. Example:
 * <pre>
 * TBD
 * </pre>
 */
@Named
public class BatchDomainImportReader extends AbstractItemHandler {

    List<DomainVo> domains;
    int domainNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the domains from the file
        domains = JsonUtils.readJson(
                new TypeReference<List<DomainVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            domainNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + domains.size() + " domains from index " + domainNo);
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (domainNo < domains.size()) {
            getLog().info("Reading domain no " + domainNo);
            return domains.get(domainNo++);
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return domainNo;
    }
}
