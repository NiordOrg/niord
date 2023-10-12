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

package org.niord.core.mailinglist.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.mailinglist.vo.MailingListVo;
import org.niord.core.util.JsonUtils;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads mailing lists from a mailing-lists.json file.
 * <p>
 * Please note, the actual mailing-list-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * Format of json file is defined by the MailingListVo class. Example:
 * <pre>
 * [
 *   {
 *      "mailingListId" : "navtex-baltico",
 *      "active" : true,
 *      "descs": [
 *          { "lang" : "da", "name" : "NAVTEX Baltico" },
 *          { "lang" : "en", "name" : "NAVTEX Baltico" }
 *      ],
 *      "triggers":[
 *          {
 *              "type" : "STATUS_CHANGE",
 *              "statusChanges" : ["CANCELLED"],
 *              "messageFilter" : "msg.promulgation('navtex').promulgate && msg.promulgation('navtex').useTransmitter('Baltico')",
 *              "scriptResourcePaths" : [ "templates/mailinglist/cancel-navtex.ftl" ],
 *              "descs" : [
 *                  { "lang" : "da", "subject" : "Annuller dansk navigationsadvarsel ${short-id}" },
 *                  { "lang" : "en", "subject" : "Cancel Danish Navigational Warning ${short-id}"}
 *              ]
 *          },
 *          { etc, etc }
 *      ]
 *   },
 *   {
 *       etc, etc
 *   }
 * ]
 * </pre>
 */
@Dependent
@Named("batchMailingListImportReader")
public class BatchMailingListImportReader extends AbstractItemHandler {

    List<MailingListVo> mailingLists;
    int mailingListNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        // Get hold of the data file
        Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

        // Load the templates from the file
        mailingLists = JsonUtils.readJson(
                new TypeReference<List<MailingListVo>>(){},
                path);

        if (prevCheckpointInfo != null) {
            mailingListNo = (Integer) prevCheckpointInfo;
        }

        getLog().info("Start processing " + mailingLists.size()
                + " mailing lists from index " + mailingListNo);
    }


    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (mailingListNo < mailingLists.size()) {
            getLog().info("Reading mailing list no " + mailingListNo);
            return mailingLists.get(mailingListNo++);
        }
        return null;
    }


    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return mailingListNo;
    }
}
