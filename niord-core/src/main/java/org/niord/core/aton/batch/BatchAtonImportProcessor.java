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
package org.niord.core.aton.batch;

import org.niord.core.aton.AtonNode;
import org.niord.core.aton.AtonService;
import org.niord.core.batch.AbstractItemHandler;
import org.niord.model.vo.aton.AtonNodeVo;

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

        if (aton.getAtonUid() == null) {
            getLog().warning("Skipping AtoN with no AtoN UID " + aton);
            return null;
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
