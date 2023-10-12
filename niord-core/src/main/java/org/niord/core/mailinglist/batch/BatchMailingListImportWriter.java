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

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.mailinglist.MailingList;
import org.niord.core.mailinglist.MailingListService;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

/**
 * Persists the templates to the database
 */
@Dependent
@Named("batchMailingListImportWriter")
public class BatchMailingListImportWriter extends AbstractItemHandler {

    @Inject
    MailingListService mailingListService;

    /** {@inheritDoc} **/
    @Override
    public void writeItems(List<Object> items) throws Exception {
        long t0 = System.currentTimeMillis();
        for (Object i : items) {
            MailingList mailingList = (MailingList) i;

            MailingList orig = mailingListService.findByMailingListId(mailingList.getMailingListId());

            if (orig == null) {
                getLog().info("Persisting new mailing list " + mailingList.getMailingListId());
                mailingListService.createMailingList(mailingList);
            } else {
                getLog().info("Updating existing mailing list " + mailingList.getMailingListId());
                mailingListService.updateMailingList(mailingList);
            }
        }
        getLog().info(String.format("Persisted %d mailing lists in %d ms", items.size(), System.currentTimeMillis() - t0));
    }
}
