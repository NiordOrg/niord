/*
 * Copyright 2024 Danish Maritime Authority.
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
package org.niord.s100.s124;

import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.*;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.Pos;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.PosList;
import dk.dma.baleen.s100.xmlbindings.s124.v2_0_0.*;
import org.junit.Test;
import org.locationtech.jts.geom.*;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;

import static org.junit.Assert.*;

/**
 * Tests for S-124 geometry conversion functionality
 */
public class S124GeometryTest extends S124TestBase {
    
    @Test
    public void testPointGeometryMapping() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(createPointGeometry(10.5, 55.5));
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertFalse("Part should have geometries", navwarnPart.getGeometries().isEmpty());
        NavwarnPart.Geometry geometry = navwarnPart.getGeometries().get(0);
        
        assertNotNull("Should have point property", geometry.getPointProperty());
        assertNotNull("Point property should have point", geometry.getPointProperty().getPoint());
        
        PointType point = geometry.getPointProperty().getPoint();
        assertNotNull("Point should have ID", point.getId());
        assertNotNull("Point should have position", point.getPos());
        
        // Verify coordinate order (lat, lon)
        Double[] coords = point.getPos().getValue();
        assertValidCoordinateOrder(coords, "Point coordinates");
        assertEquals("Latitude should match", 55.5, coords[0], 0.001);
        assertEquals("Longitude should match", 10.5, coords[1], 0.001);
    }
    
    @Test
    public void testPolygonGeometryMapping() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(createPolygonGeometry());
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertFalse("Part should have geometries", navwarnPart.getGeometries().isEmpty());
        NavwarnPart.Geometry geometry = navwarnPart.getGeometries().get(0);
        
        assertNotNull("Should have surface property", geometry.getSurfaceProperty());
        assertNotNull("Surface property should have surface", geometry.getSurfaceProperty().getSurface());
        
        SurfaceType surface = geometry.getSurfaceProperty().getSurface();
        assertNotNull("Surface should have ID", surface.getId());
        assertNotNull("Surface should have patches", surface.getPatches());
        assertFalse("Surface should have patches", surface.getPatches().getAbstractSurfacePatches().isEmpty());
    }
    
    @Test
    public void testLineStringGeometryMapping() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(createLineStringGeometry());
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertFalse("Part should have geometries", navwarnPart.getGeometries().isEmpty());
        NavwarnPart.Geometry geometry = navwarnPart.getGeometries().get(0);
        
        assertNotNull("Should have curve property", geometry.getCurveProperty());
        assertNotNull("Curve property should have curve", geometry.getCurveProperty().getCurve());
        
        CurveType curve = geometry.getCurveProperty().getCurve();
        assertNotNull("Curve should have ID", curve.getId());
        assertNotNull("Curve should have segments", curve.getSegments());
        assertFalse("Curve should have segments", curve.getSegments().getAbstractCurveSegments().isEmpty());
    }
    
    @Test
    public void testMultipleGeometriesMapping() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        
        FeatureCollection fc = new FeatureCollection();
        
        // Add point
        Feature pointFeature = new Feature();
        pointFeature.setGeometry(geometryFactory.createPoint(new Coordinate(10.0, 55.0)));
        fc.getFeatures().add(pointFeature);
        
        // Add polygon
        Feature polygonFeature = new Feature();
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(11.0, 56.0),
            new Coordinate(11.0, 56.5),
            new Coordinate(11.5, 56.5),
            new Coordinate(11.5, 56.0),
            new Coordinate(11.0, 56.0)
        };
        polygonFeature.setGeometry(geometryFactory.createPolygon(coords));
        fc.getFeatures().add(polygonFeature);
        
        part.setGeometry(fc);
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertEquals("Should have 2 geometries", 2, navwarnPart.getGeometries().size());
        
        // Verify point geometry
        NavwarnPart.Geometry pointGeom = navwarnPart.getGeometries().get(0);
        assertNotNull("Second geometry should be surface", pointGeom.getPointProperty());
        
        // Verify polygon geometry
        NavwarnPart.Geometry surfaceGeom = navwarnPart.getGeometries().get(1);
        assertNotNull(surfaceGeom.getSurfaceProperty());
    }
    
    @Test
    public void testGeometryIdAssignment() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(createPointGeometry(10.0, 55.0));
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        NavwarnPart.Geometry geometry = navwarnPart.getGeometries().get(0);
        
        String geometryId = geometry.getPointProperty().getPoint().getId();
        assertNotNull("Geometry should have ID", geometryId);
        assertTrue("Geometry ID should start with G.", geometryId.startsWith("G."));
        assertTrue("Geometry ID should contain part ID", geometryId.contains(navwarnPart.getId()));
    }
    
    @Test
    public void testBoundingBoxCalculation() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(createPolygonGeometry()); // Creates polygon from 10.5,55.5 to 11.0,56.0
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        assertNotNull("Dataset should have bounding box", dataset.getBoundedBy());
        assertNotNull("Should have envelope", dataset.getBoundedBy().getEnvelope());
        
        var envelope = dataset.getBoundedBy().getEnvelope();
        assertEquals("Should use WGS84", "EPSG:4326", envelope.getSrsName());
        
        // Verify coordinate order (lat,lon)
        Double[] lowerCorner = envelope.getLowerCorner().getValue();
        Double[] upperCorner = envelope.getUpperCorner().getValue();
        
        assertValidCoordinateOrder(lowerCorner, "Lower corner");
        assertValidCoordinateOrder(upperCorner, "Upper corner");
        
        // Verify bounds (note: coordinate order is lat,lon)
        assertEquals("Min latitude", 55.5, lowerCorner[0], 0.001);
        assertEquals("Min longitude", 10.5, lowerCorner[1], 0.001);
        assertEquals("Max latitude", 56.0, upperCorner[0], 0.001);
        assertEquals("Max longitude", 11.0, upperCorner[1], 0.001);
    }
    
    @Test
    public void testMessagePartBoundingBox() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(createPointGeometry(12.0, 57.0));
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertNotNull("Part should have bounding box", navwarnPart.getBoundedBy());
        var envelope = navwarnPart.getBoundedBy().getEnvelope();
        
        Double[] lowerCorner = envelope.getLowerCorner().getValue();
        Double[] upperCorner = envelope.getUpperCorner().getValue();
        
        // For a point, lower and upper corners should be the same
        assertValidCoordinateOrder(lowerCorner, "Part lower corner");
        assertValidCoordinateOrder(upperCorner, "Part upper corner");
        assertEquals("Point latitude", 57.0, lowerCorner[0], 0.001);
        assertEquals("Point longitude", 12.0, lowerCorner[1], 0.001);
    }
    
    @Test
    public void testGeometryConversion() {
        // Test the geometry converter directly
        GeometryS124Converter converter = new GeometryS124Converter();
        
        // Test point conversion
        Point point = geometryFactory.createPoint(new Coordinate(10.0, 55.0));
        var s124Geometries = converter.geometryToS124PointCurveSurfaceGeometry(point);
        
        assertEquals("Should have one geometry", 1, s124Geometries.size());
        assertTrue(s124Geometries.get(0) instanceof PointProperty);
        
        PointProperty pointProp = (PointProperty) s124Geometries.get(0);
        Pos pos = pointProp.getPoint().getPos();
        
        // Verify lat,lon order
        Double[] coords = pos.getValue();
        assertEquals("Should be latitude first", 55.0, coords[0], 0.001);
        assertEquals("Should be longitude second", 10.0, coords[1], 0.001);
    }
    
    @Test
    public void testCoordinateOrderInPosList() {
        // Test line string with multiple coordinates
        GeometryS124Converter converter = new GeometryS124Converter();
        
        Coordinate[] lineCoords = new Coordinate[] {
            new Coordinate(10.0, 55.0),
            new Coordinate(10.5, 55.5),
            new Coordinate(11.0, 56.0)
        };
        LineString line = geometryFactory.createLineString(lineCoords);
        
        var s124Geometries = converter.geometryToS124PointCurveSurfaceGeometry(line);
        assertTrue("Should be curve property", s124Geometries.get(0) instanceof CurveProperty);
        
        CurveProperty curveProp = (CurveProperty) s124Geometries.get(0);
        var segments = curveProp.getCurve().getSegments().getAbstractCurveSegments();
        assertFalse("Should have segments", segments.isEmpty());
        
        // Extract coordinates and verify order
        var segment = segments.get(0).getValue();
        if (segment instanceof dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.LineStringSegmentType) {
            var lineSegment = (dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.LineStringSegmentType) segment;
            PosList posList = lineSegment.getPosList();
            Double[] values = posList.getValue();
            
            // Should have 6 values (3 points * 2 coordinates each)
            assertEquals("Should have 6 coordinate values", 6, values.length);
            
            // First point: lat=55.0, lon=10.0
            assertEquals("First point latitude", 55.0, values[0], 0.001);
            assertEquals("First point longitude", 10.0, values[1], 0.001);
            
            // Second point: lat=55.5, lon=10.5
            assertEquals("Second point latitude", 55.5, values[2], 0.001);
            assertEquals("Second point longitude", 10.5, values[3], 0.001);
        }
    }
    
    // Helper methods
    
    private FeatureCollection createLineStringGeometry() {
        FeatureCollection fc = new FeatureCollection();
        Feature feature = new Feature();
        
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(10.0, 55.0),
            new Coordinate(10.5, 55.5),
            new Coordinate(11.0, 56.0)
        };
        
        LineString lineString = geometryFactory.createLineString(coords);
        feature.setGeometry(lineString);
        feature.getProperties().put("name", "Test Line");
        
        fc.getFeatures().add(feature);
        return fc;
    }
    
    private java.util.List<NavwarnPart> findNavwarnParts(Dataset dataset) {
        return dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements()
                .stream()
                .filter(NavwarnPart.class::isInstance)
                .map(NavwarnPart.class::cast)
                .toList();
    }
}