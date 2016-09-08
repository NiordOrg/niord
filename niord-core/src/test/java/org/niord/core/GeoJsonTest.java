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

package org.niord.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.geojson.JtsConverter;
import org.niord.core.geojson.PlainTextConverter;
import org.niord.model.geojson.FeatureCollectionVo;
import org.niord.model.geojson.GeoJsonVo;
import org.niord.model.geojson.GeometryVo;
import org.niord.model.geojson.MultiPointVo;
import org.niord.model.geojson.PolygonVo;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test GeoJson classes
 */
public class GeoJsonTest {

    @Test
    public void loadGeoJson() {


        ObjectMapper mapper = new ObjectMapper();

        try {
            CoordCounter counter = new CoordCounter();
            GeoJsonVo result = mapper.readValue(getClass().getResource("/dk.json"), GeoJsonVo.class);

            System.out.println(result);

            result.visitCoordinates(coords -> {
                double tmp = coords[0];
                coords[0] = coords[1];
                coords[1] = tmp;
            });
            System.out.println(result);

            GeometryVo geometry = ((FeatureCollectionVo)result).getFeatures()[0].getGeometry();
            geometry.visitCoordinates(counter);
            System.out.println("#coords = " + counter);

            com.vividsolutions.jts.geom.Geometry jts = JtsConverter.toJts(geometry);
            System.out.println("-> JTS " + jts);
            geometry = JtsConverter.fromJts(jts);
            System.out.println("<- JTS " + geometry);
            geometry.visitCoordinates(counter.reset());
            System.out.println("#coords = " + counter);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void serializeGeoJson() {

        ObjectMapper mapper = new ObjectMapper();

        try {
            GeoJsonVo geojson = mapper.readValue(getClass().getResource("/featurename.json"), GeoJsonVo.class);
            FeatureCollectionVo fc = (FeatureCollectionVo) geojson;
            assertEquals(3, fc.getFeatures().length); // NB: Last feature is a "buffer" affected area feature

            List<GeoJsonUtils.SerializedFeature> coords = GeoJsonUtils.serializeFeatureCollection(fc, "da");

            assertEquals(coords.size(), 2);
            assertEquals(coords.get(0).getCoordinates().size(), 1);
            assertNull(coords.get(0).getName());
            assertEquals(coords.get(1).getCoordinates().size(), 4);
            assertEquals(coords.get(1).getName(), "ged");
            assertEquals(coords.get(1).getCoordinates().get(0).getName(), "aa");

            coords.forEach(sf -> {
                System.out.println("Feature: " + sf.getName());
                sf.getCoordinates().forEach(sc -> {
                    StringBuilder str = new StringBuilder();
                    str.append(String.format("lat=%.2f, lon=%.2f", sc.getCoordinates()[1], sc.getCoordinates()[0]));
                    if (StringUtils.isNotBlank(sc.getName())) {
                        str.append(", ").append(sc.getName());
                    }
                    System.out.println("  " + str);
                });
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Test
    public void plainTextConverterTest() throws Exception {

        String[] languages = {"en", "da"};
        PlainTextConverter converter = PlainTextConverter.newInstance(languages);

        String text = "\nPolygon, Goat-Ice\n54° 41,000'N - 010° 35,000'E\n54° 35,566'N - 010° 35,010'E\n54° 38,397'N - 010° 25,125'E";
        FeatureCollectionVo result = converter.fromPlainText(text);
        System.out.println("Parsed: " + result);
        assertNotNull(result);
        assertEquals(1, result.getFeatures().length);
        assertEquals(PolygonVo.class, result.getFeatures()[0].getGeometry().getClass());
        assertEquals("Goat-Ice", result.getFeatures()[0].getProperties().get("name:da"));
        assertEquals("Goat-Ice", result.getFeatures()[0].getProperties().get("name:en"));

        String formattedGeoJson = converter.toPlainText(result);
        assertNotNull(formattedGeoJson);
        System.out.println("Formatted:\n" + formattedGeoJson);


        text = "1) 54° 45,7' N 10° 29,1' E, Ærø S.\n2) 54° 41,2' N 10° 36,9' E, Keldsnor SW.\n3) 54° 38,3' N 10° 40,1' E, Keldsnor S.";
        result = converter.fromPlainText(text);
        assertEquals(MultiPointVo.class, result.getFeatures()[0].getGeometry().getClass());
        System.out.println("Parsed: " + result);

        formattedGeoJson = converter.toPlainText(result);
        System.out.println("Formatted:\n" + formattedGeoJson);


        text = "Point, da: Ubåd U-9, en: Submarine U-9.\n54° 45,7' N 10° 29,1' E, da: Ærø S. en: Aeroe S.";
        result = converter.fromPlainText(text);
        assertEquals("Ærø S.", result.getFeatures()[0].getProperties().get("name:0:da"));
        assertEquals("Aeroe S.", result.getFeatures()[0].getProperties().get("name:0:en"));
        System.out.println("Parsed: " + result);

        formattedGeoJson = converter.toPlainText(result);
        System.out.println("Formatted:\n" + formattedGeoJson);
    }



    class CoordCounter implements Consumer<double[]> {
        int count = 0;
        @Override
        public void accept(double[] v) {
            count++;
        }

        public Consumer<double[]> reset() {
            count = 0;
            return this;
        }

        public String toString() {
        return String.valueOf(count);
    }
    }
}
