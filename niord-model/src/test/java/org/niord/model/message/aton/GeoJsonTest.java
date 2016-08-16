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
package org.niord.model.message.aton;

import org.junit.Test;
import org.niord.model.message.geojson.FeatureCollectionVo;
import org.niord.model.message.geojson.FeatureVo;
import org.niord.model.message.geojson.GeometryCollectionVo;
import org.niord.model.message.geojson.GeometryVo;
import org.niord.model.message.geojson.MultiPointVo;
import org.niord.model.message.geojson.PointVo;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

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
