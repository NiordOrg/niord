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
package org.niord.core.message.batch;

import org.niord.core.batch.AbstractItemHandler;
import org.niord.core.message.MessageService;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Processes legacy NM messages
 */
@Named
public class BatchMessageImportProcessor extends AbstractItemHandler {

    @Inject
    MessageService messageService;

    /** {@inheritDoc} **/
    @Override
    public Object processItem(Object item) throws Exception {

        Integer id = (Integer)item;



        // No change, ignore...
        getLog().info("NM " + id);
        return null;
    }
}
