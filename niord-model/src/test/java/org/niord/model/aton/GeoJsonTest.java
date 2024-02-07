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
package org.niord.model.aton;

import org.junit.Test;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.FeatureVo;
import org.niord.model.geojson.GeometryCollectionVo;
import org.niord.model.geojson.GeometryVo;
import org.niord.model.geojson.MultiPointVo;
import org.niord.model.geojson.PointVo;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;

/**
 * Unit tests for the OSM AtoN model
 */
public class GeoJsonTest {

    /** Test generating XML from GeoJson */
    @Test
    public void testGeoJsonXml() throws Exception {

        MultiPointVo mp = new MultiPointVo();
        mp.setCoordinates(new double[][] { { 9.73388, 55.559716 }, { 9.7353833, 55.5591666 } });

        PointVo p = new PointVo();
        p.setCoordinates(new double[] { 9.73388, 55.559716 });

        GeometryCollectionVo gc = new GeometryCollectionVo();
        gc.setGeometries(new GeometryVo[] { mp, p});

        FeatureVo feature = new FeatureVo();
        feature.setGeometry(gc);
        feature.getProperties().put("ged", "mega-ged");

        FeatureCollectionVo fc = new FeatureCollectionVo();
        fc.setFeatures(new FeatureVo[] { feature } );

        JAXBContext jaxbContext = JAXBContext.newInstance(FeatureCollectionVo.class, GeometryVo.class, MultiPointVo.class, FeatureVo.class);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(fc, System.out);
    }


}
