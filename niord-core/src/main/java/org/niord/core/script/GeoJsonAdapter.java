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
package org.niord.core.script;

import freemarker.template.AdapterTemplateModel;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateSequenceModel;
import freemarker.template.WrappingTemplateModel;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.model.geojson.FeatureCollectionVo;

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