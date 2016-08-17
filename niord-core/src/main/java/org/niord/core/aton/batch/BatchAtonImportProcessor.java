/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.core.aton.batch;

import org.niord.core.aton.AtonNode;
import org.niord.core.aton.AtonService;
import org.niord.core.aton.AtonTag;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.model.aton.AtonNodeVo;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Filters AtoNs that need to be a added or updated
 *
 * TODO: we should probably detach the original AtoN looked up in processItem()
 */
@Named
public class BatchAtonImportProcessor extends AbstractItemHandler {

    @Inject
    AtonService atonService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        // Convert the item to an AtoN node
        AtonNode aton = toAtonNode(item);

        if (aton == null) {
            return null;
        }

        // TODO: Figure out a good strategy for handling AtoN's without an AtoN UID.
        // Either skip them, or look up by "seamark:type" + coordinates...
        if (aton.getAtonUid() == null) {
            aton.updateTag(AtonTag.TAG_ATON_UID, "aton-import-" + aton.getId());
            //getLog().warning("Skipping AtoN with no AtoN UID " + aton);
            //return null;
        }

        // Look up any existing AtoN with the same AtoN UID
        AtonNode orig = atonService.findByAtonUid(aton.getAtonUid());

        if (orig == null) {
            // Persist new AtoN
            getLog().info("Persisting new AtoN");
            return aton;

        } else if (orig.hasChanged(aton)) {
            // Update original
            getLog().info("Updating AtoN " + orig.getId());
            mergeAtonNodes(orig, aton);
            return orig;
        }

        // No change, ignore...
        getLog().info("Ignoring unchanged AtoN " + orig.getId());
        return null;
    }


    /**
     * Parses the next AtonNode from the current Excel row
     * @return the parsed AtonNode
     */
    protected AtonNode toAtonNode(Object item) throws Exception {
        AtonNodeVo atonVo = (AtonNodeVo)item;
        return new AtonNode(atonVo);
    }


    /**
     * Called when the newly parsed AtoN is an update to an existing AtoN.
     * Merges the AtoN template into the original AtoN.
     *
     * Sub-classes can override to provide customized behaviour.
     *
     * @param original the original AtoN that should be updated
     * @param aton the new AtoN template
     */
    protected void mergeAtonNodes(AtonNode original, AtonNode aton) {

        // Default behaviour - just update the original from the new AtoN
        original.updateNode(aton);
    }
}
