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
package org.niord.core.fm;

import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.Version;
import org.niord.model.message.geojson.FeatureCollectionVo;

/**
 * Custom object wrapper.
 */
@SuppressWarnings("unused")
public class NiordAppObjectWrapper extends DefaultObjectWrapper {

    /** Constructor */
    public NiordAppObjectWrapper(Version incompatibleImprovements) {
        super(incompatibleImprovements);
    }

    /** {@inheritDoc} */
    @Override
    protected TemplateModel handleUnknownType(final Object obj) throws TemplateModelException {
        if (obj instanceof FeatureCollectionVo) {
            return new GeoJsonAdapter((FeatureCollectionVo) obj, this);
        }

        return super.handleUnknownType(obj);
    }
}
