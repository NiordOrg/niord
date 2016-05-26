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

import freemarker.template.AdapterTemplateModel;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateSequenceModel;
import freemarker.template.WrappingTemplateModel;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.model.vo.geojson.FeatureCollectionVo;

import java.util.List;

/**
 * Converts GeoJson feature collections to a serialized list of named coordinates.
 */
@SuppressWarnings("all")
public class GeoJsonAdapter
        extends WrappingTemplateModel
        implements TemplateSequenceModel, AdapterTemplateModel {

    private final List<GeoJsonUtils.SerializedFeature> fc;

    /** Constructor **/
    public GeoJsonAdapter(FeatureCollectionVo featureCollection, ObjectWrapper ow) {
        super(ow);
        this.fc = GeoJsonUtils.serializeFeatureCollection(featureCollection, null);
    }

    /** {@inheritDoc} **/
    @Override
    public int size() throws TemplateModelException {
        return fc == null ? 0 : fc.size();
    }

    /** {@inheritDoc} **/
    @Override
    public TemplateModel get(int index) throws TemplateModelException {
        if (fc == null || index < 0 || index >= fc.size()) {
            return null;
        }
        return wrap(fc.get(index));
    }

    /** {@inheritDoc} **/
    @Override
    public Object getAdaptedObject(Class hint) {
        return fc;
    }

}