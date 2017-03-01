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

import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.Version;
import org.niord.model.geojson.FeatureCollectionVo;

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
