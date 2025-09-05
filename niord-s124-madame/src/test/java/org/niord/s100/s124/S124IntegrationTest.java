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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.niord.core.area.Area;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;
import org.niord.core.message.MessagePartDesc;
import org.niord.core.message.MessageTag;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.Type;

import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.Dataset;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPart;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPreamble;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.References;

/**
 * Integration tests for S-124 mapping with complex real-world scenarios
 */
public class S124IntegrationTest extends S124TestBase {

    @Test
    public void testCompleteNavigationalWarningMapping() {
        // Arrange - Create a comprehensive navigational warning
        Message message = createCompleteMessage();

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert - Comprehensive validation
        validateCompleteDataset(dataset, message);
    }

    @Test
    public void testMultiPartMessageWithReferences() {
        // Arrange
        Message referencedMessage = createBasicMessage();
        referencedMessage.setId(999);
        referencedMessage.setShortId("DK-999-23");
        referencedMessage.setNumber(99);

        Message message = createBasicMessage();

        // Add multiple parts
        MessagePart part1 = createBasicMessagePart(1);
        part1.setGeometry(createPointGeometry(10.0, 55.0));

        MessagePart part2 = createBasicMessagePart(2);
        part2.setGeometry(createPolygonGeometry());

        MessagePart part3 = createBasicMessagePart(3);
        part3.setGeometry(createLineStringGeometry());

        message.getParts().add(part1);
        message.getParts().add(part2);
        message.getParts().add(part3);

        // Add references
        message.getReferences().add(createReference(referencedMessage, ReferenceType.UPDATE));

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        validateMultiPartDataset(dataset);
    }

    @Test
    public void testCancellationScenario() {
        // Arrange - Message that cancels another message
        Message cancelledMessage = createBasicMessage();
        cancelledMessage.setId(888);
        cancelledMessage.setShortId("DK-888-23");

        Message cancellationMessage = createBasicMessage();
        cancellationMessage.setShortId("DK-001-24-CANCEL");

        MessagePart part = createBasicMessagePart(1);
        MessagePartDesc desc = part.getDesc("en");
        desc.setSubject("Cancellation");
        desc.setDetails("The previous navigational warning DK-888-23 is hereby cancelled.");

        cancellationMessage.getParts().add(part);
        cancellationMessage.getReferences().add(createReference(cancelledMessage, ReferenceType.CANCELLATION));

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, cancellationMessage);

        // Assert
        validateCancellationDataset(dataset);
    }

    @Test
    public void testMultiLanguageScenario() {
        // Arrange
        Message message = createBasicMessage();
        addMultiLanguageDescriptions(message);

        MessagePart part = createBasicMessagePart(1);

        // Add multi-language descriptions to part
        MessagePartDesc dkDesc = new MessagePartDesc();
        dkDesc.setLang("da");
        dkDesc.setSubject("Dansk advarsel");
        dkDesc.setDetails("Detaljeret beskrivelse på dansk");
        part.getDescs().add(dkDesc);

        MessagePartDesc deDesc = new MessagePartDesc();
        deDesc.setLang("de");
        deDesc.setSubject("Deutsche Warnung");
        deDesc.setDetails("Detaillierte Beschreibung auf Deutsch");
        part.getDescs().add(deDesc);

        message.getParts().add(part);

        // Test with different language preferences
        Dataset enDataset = S124Mapper.map(datasetInfo, message, "en");
        Dataset daDataset = S124Mapper.map(datasetInfo, message, "da");
        Dataset deDataset = S124Mapper.map(datasetInfo, message, "de");

        // Assert
        validateMultiLanguageDatasets(enDataset, daDataset, deDataset);
    }

    @Test
    public void testComplexGeometryScenario() {
        // Arrange - Message with multiple complex geometries
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);

        FeatureCollection fc = new FeatureCollection();

        // Add multiple geometry types
        addPointToFeatureCollection(fc, 10.0, 55.0, "Lighthouse");
        addPolygonToFeatureCollection(fc, "Restricted Area");
        addLineStringToFeatureCollection(fc, "Cable Route");

        part.setGeometry(fc);
        message.getParts().add(part);

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        validateComplexGeometryDataset(dataset);
    }

    @Test
    public void testRealWorldScenario() {
        // Arrange - Simulate a real-world navigational warning
        Message message = createRealWorldMessage();

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        validateRealWorldDataset(dataset);
    }

    // Helper methods for creating test data

    private Message createCompleteMessage() {
        Message message = createBasicMessage();
        message.setType(Type.COASTAL_WARNING);

        // Add areas
        message.getAreas().add(createTestArea("GREAT_BELT", "Great Belt"));
        message.getAreas().add(createTestArea("KATTEGAT", "Kattegat"));

        // Add charts
        Date chartDate = Date.from(LocalDateTime.of(2024, 1, 1, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant());
        message.getCharts().add(createTestChart("102", chartDate));
        message.getCharts().add(createTestChart("103", chartDate));

        // Add categories
        message.getCategories().add(createTestCategory("Obstruction"));

        // Add tags
        MessageTag tag = new MessageTag();
        tag.setName("CAUTION");
        message.getTags().add(tag);

        // Add parts with different geometries
        MessagePart part1 = createBasicMessagePart(1);
        part1.setGeometry(createPointGeometry(10.5, 55.5));

        MessagePart part2 = createBasicMessagePart(2);
        part2.setGeometry(createPolygonGeometry());

        message.getParts().add(part1);
        message.getParts().add(part2);

        return message;
    }

    private Message createRealWorldMessage() {
        Message message = createBasicMessage();
        message.setShortId("DK-001-24");
        message.setType(Type.COASTAL_WARNING);

        // Real-world description
        var desc = message.getDesc("en");
        desc.setTitle("UNDERWATER OPERATIONS - GREAT BELT");
        desc.setVicinity("Great Belt, East of Sprogø");

        // Add area
        Area area = createTestArea("GREAT_BELT_EAST", "Great Belt East");
        message.getAreas().add(area);

        // Add affected charts
        Date chartDate = Date.from(LocalDateTime.of(2024, 1, 1, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant());
        message.getCharts().add(createTestChart("102", chartDate));

        // Add category
        message.getCategories().add(createTestCategory("Obstruction"));

        // Add message part with real-world content
        MessagePart part = createBasicMessagePart(1);
        MessagePartDesc partDesc = part.getDesc("en");
        partDesc.setSubject("Underwater cable laying operations");
        partDesc.setDetails("<p>Underwater cable laying operations in progress.</p>" +
                "<p>Area to be avoided:</p>" +
                "<ul><li>Within 500m of the operation</li></ul>" +
                "<p>Duration: 15 JAN - 31 MAR 2024</p>");

        // Add precise geometry for the operation area
        part.setGeometry(createOperationAreaGeometry());

        message.getParts().add(part);

        return message;
    }

    private FeatureCollection createLineStringGeometry() {
        FeatureCollection fc = new FeatureCollection();
        Feature feature = new Feature();

        Coordinate[] coords = new Coordinate[] {
            new Coordinate(10.0, 55.0),
            new Coordinate(10.5, 55.5),
            new Coordinate(11.0, 56.0)
        };

        feature.setGeometry(geometryFactory.createLineString(coords));
        feature.getProperties().put("name", "Test Line");

        fc.getFeatures().add(feature);
        return fc;
    }

    private FeatureCollection createOperationAreaGeometry() {
        FeatureCollection fc = new FeatureCollection();
        Feature feature = new Feature();

        // Create a realistic operation area in Great Belt
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(10.8, 55.3),
            new Coordinate(10.8, 55.4),
            new Coordinate(11.0, 55.4),
            new Coordinate(11.0, 55.3),
            new Coordinate(10.8, 55.3)
        };

        feature.setGeometry(geometryFactory.createPolygon(coords));
        feature.getProperties().put("name", "Cable Laying Area");
        feature.getProperties().put("restriction", "avoid");

        fc.getFeatures().add(feature);
        return fc;
    }

    private void addPointToFeatureCollection(FeatureCollection fc, double lon, double lat, String name) {
        Feature feature = new Feature();
        feature.setGeometry(geometryFactory.createPoint(new Coordinate(lon, lat)));
        feature.getProperties().put("name", name);
        fc.getFeatures().add(feature);
    }

    private void addPolygonToFeatureCollection(FeatureCollection fc, String name) {
        Feature feature = new Feature();
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(10.5, 55.5),
            new Coordinate(10.5, 56.0),
            new Coordinate(11.0, 56.0),
            new Coordinate(11.0, 55.5),
            new Coordinate(10.5, 55.5)
        };
        feature.setGeometry(geometryFactory.createPolygon(coords));
        feature.getProperties().put("name", name);
        fc.getFeatures().add(feature);
    }

    private void addLineStringToFeatureCollection(FeatureCollection fc, String name) {
        Feature feature = new Feature();
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(9.0, 54.0),
            new Coordinate(10.0, 55.0),
            new Coordinate(11.0, 56.0)
        };
        feature.setGeometry(geometryFactory.createLineString(coords));
        feature.getProperties().put("name", name);
        fc.getFeatures().add(feature);
    }

    // Validation methods

    private void validateCompleteDataset(Dataset dataset, Message message) {
        assertNotNull("Dataset should not be null", dataset);

        // Validate dataset structure
        assertNotNull("Dataset should have members", dataset.getMembers());
        var members = dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements();
        assertFalse("Dataset should have members", members.isEmpty());

        // Should have: 1 preamble + 2 parts + 0 references = 3 members
        assertEquals("Should have preamble and parts", 3, members.size());

        // Validate preamble
        NavwarnPreamble preamble = findPreamble(dataset);
        assertNotNull(preamble);
        assertEquals("Should have 2 areas", 2, preamble.getGeneralAreas().size());
        assertEquals("Should have 2 charts", 2, preamble.getAffectedChartPublications().size());
        assertNotNull(preamble.getNavwarnTypeGeneral());

        // Validate parts
        var parts = findNavwarnParts(dataset);
        assertEquals("Should have 2 parts", 2, parts.size());

        // Each part should have geometry
        assertFalse("Second part should have geometry", parts.get(0).getGeometries().isEmpty());
        assertFalse(parts.get(1).getGeometries().isEmpty());

        // Each part should have restriction
        assertNotNull("First part should have restriction", parts.get(0).getRestriction());
        assertNotNull("Second part should have restriction", parts.get(1).getRestriction());
    }

    private void validateMultiPartDataset(Dataset dataset) {
        var parts = findNavwarnParts(dataset);
        assertEquals("Should have 3 parts", 3, parts.size());

        // Verify different geometry types
        assertTrue("Should have surface geometry", parts.stream().anyMatch(p -> !p.getGeometries().isEmpty() &&
                p.getGeometries().get(0).getPointProperty() != null));
        assertTrue(parts.stream().anyMatch(p -> !p.getGeometries().isEmpty() &&
                p.getGeometries().get(0).getSurfaceProperty() != null));
        assertTrue("Should have curve geometry", parts.stream().anyMatch(p -> !p.getGeometries().isEmpty() &&
                p.getGeometries().get(0).getCurveProperty() != null));

        // Should have references
        var references = findReferences(dataset);
        assertEquals("Should have 1 reference", 1, references.size());
        assertFalse(references.get(0).isNoMessageOnHand());
    }

    private void validateCancellationDataset(Dataset dataset) {
        var references = findReferences(dataset);
        assertEquals("Should have 1 reference", 1, references.size());
        assertTrue(references.get(0).isNoMessageOnHand());

        var parts = findNavwarnParts(dataset);
        assertEquals("Should have 1 part", 1, parts.size());

        String text = parts.get(0).getWarningInformation().getInformations().get(0).getText();
        assertTrue(text.contains("cancelled"));
    }

    private void validateMultiLanguageDatasets(Dataset enDataset, Dataset daDataset, Dataset deDataset) {
        // Verify preambles have titles in all languages
        NavwarnPreamble enPreamble = findPreamble(enDataset);
        assertEquals("EN dataset should have all language titles", 3, enPreamble.getNavwarnTitles().size());

        // Verify part content is in correct language
        var enParts = findNavwarnParts(enDataset);
        var daParts = findNavwarnParts(daDataset);
        var deParts = findNavwarnParts(deDataset);

        assertEquals("en", enParts.get(0).getWarningInformation().getInformations().get(0).getLanguage());
        assertEquals("da", daParts.get(0).getWarningInformation().getInformations().get(0).getLanguage());
        assertEquals("Should have 1 part", "de", deParts.get(0).getWarningInformation().getInformations().get(0).getLanguage());

        assertTrue(daParts.get(0).getWarningInformation().getInformations().get(0).getText().contains("dansk"));
        assertTrue(deParts.get(0).getWarningInformation().getInformations().get(0).getText().contains("Deutsch"));
    }

    private void validateComplexGeometryDataset(Dataset dataset) {
        var parts = findNavwarnParts(dataset);
        assertEquals(1, parts.size());

        NavwarnPart part = parts.get(0);
        assertEquals("Should have 3 geometries (point, polygon, line)", 3, part.getGeometries().size());

        // Verify each geometry has an ID
        for (var geom : part.getGeometries()) {
            if (geom.getPointProperty() != null) {
                assertNotNull("Point should have ID", geom.getPointProperty().getPoint().getId());
            }
            if (geom.getSurfaceProperty() != null) {
                assertNotNull("Surface should have ID", geom.getSurfaceProperty().getSurface().getId());
            }
            if (geom.getCurveProperty() != null) {
                assertNotNull("Curve should have ID", geom.getCurveProperty().getCurve().getId());
            }
        }
    }

    private void validateRealWorldDataset(Dataset dataset) {
        NavwarnPreamble preamble = findPreamble(dataset);

        // Verify realistic content
        assertTrue(preamble.getNavwarnTitles().get(0).getText().contains("UNDERWATER OPERATIONS"));
        assertEquals(1, preamble.getGeneralAreas().size());
        assertEquals("Should contain operation details", 1, preamble.getAffectedChartPublications().size());

        var parts = findNavwarnParts(dataset);
        assertEquals("Should contain distance information", 1, parts.size());

        NavwarnPart part = parts.get(0);
        String details = part.getWarningInformation().getInformations().get(0).getText();
        assertTrue(details.contains("cable laying"));
        assertTrue(details.contains("500m"));

        // Verify geometry is present and reasonable
        assertFalse("Should have operation area geometry", part.getGeometries().isEmpty());
    }

    // Helper methods

    private NavwarnPreamble findPreamble(Dataset dataset) {
        return dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements()
                .stream()
                .filter(NavwarnPreamble.class::isInstance)
                .map(NavwarnPreamble.class::cast)
                .findFirst()
                .orElse(null);
    }

    private java.util.List<NavwarnPart> findNavwarnParts(Dataset dataset) {
        return dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements()
                .stream()
                .filter(NavwarnPart.class::isInstance)
                .map(NavwarnPart.class::cast)
                .toList();
    }

    private java.util.List<References> findReferences(Dataset dataset) {
        return dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements()
                .stream()
                .filter(References.class::isInstance)
                .map(References.class::cast)
                .toList();
    }
}