package org.niord.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.geojson.JtsConverter;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.niord.model.vo.geojson.GeoJsonVo;
import org.niord.model.vo.geojson.GeometryVo;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
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

            coords.stream().forEach(sf -> {
                System.out.println("Feature: " + sf.getName());
                sf.getCoordinates().stream().forEach(sc -> {
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
