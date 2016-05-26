package org.niord.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.niord.core.geojson.JtsConverter;
import org.niord.model.vo.geojson.FeatureCollectionVo;
import org.niord.model.vo.geojson.GeoJsonVo;
import org.niord.model.vo.geojson.GeometryVo;

import java.io.IOException;
import java.util.function.Consumer;

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
