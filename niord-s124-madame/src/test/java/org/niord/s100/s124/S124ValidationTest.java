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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.niord.core.message.DateInterval;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;
import org.niord.core.message.MessagePartDesc;
import org.niord.model.message.MainType;

import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.Dataset;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPart;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPreamble;

/**
 * Tests for S-124 validation and error handling
 */
public class S124ValidationTest extends S124TestBase {

    @Test
    public void testNullDatasetInfoValidation() {
        // Arrange
        Message message = createBasicMessage();

        // Act & Assert
        try {
            S124Mapper.map(null, message);
            fail("Should throw exception for null dataset info");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testNullMessageValidation() {
        // Act & Assert
        try {
            S124Mapper.map(datasetInfo, null);
            fail("Should throw exception for null message");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testNonNavigationalWarningValidation() {
        // Arrange
        Message message = createBasicMessage();
        message.setMainType(MainType.NM); // Notice to Mariners, not Navigation Warning

        // Act & Assert
        try {
            S124Mapper.map(datasetInfo, message);
            fail("Should throw exception for non-NW messages");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testMessageWithoutPublishDateFrom() {
        // Arrange
        Message message = createBasicMessage();
        message.setPublishDateFrom(null);
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Act - Should not throw exception but handle gracefully
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should create dataset even without publish date", dataset);
        // The year field in message series identifier should not be set
        // This is handled gracefully in the mapper
    }

    @Test
    public void testMessageWithoutMessageSeries() {
        // Arrange
        Message message = createBasicMessage();
        message.setMessageSeries(null);
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Act - Should not throw exception but handle gracefully
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should create dataset even without message series", dataset);
    }

    @Test
    public void testMessageWithoutNumber() {
        // Arrange
        Message message = createBasicMessage();
        message.setNumber(null);
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Act - Should not throw exception but handle gracefully
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should create dataset even without number", dataset);
    }

    @Test
    public void testMessageWithoutType() {
        // Arrange
        Message message = createBasicMessage();
        message.setType(null);
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Act - Should not throw exception but handle gracefully
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should create dataset even without type", dataset);
    }

    @Test
    public void testMessagePartWithoutEventDates() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.getEventDates().clear(); // Remove event dates
        message.getParts().add(part);

        // Act - Should not throw exception
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle message part without event dates", dataset);
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        assertTrue("Should have no fixed date ranges", parts.get(0).getFixedDateRanges().isEmpty());
    }

    @Test
    public void testMessagePartWithoutDescription() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.getDescs().clear(); // Remove descriptions
        message.getParts().add(part);

        // Act - Should not throw exception
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle message part without description", dataset);
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        // Warning information should still be created but without content
        assertNotNull(parts.get(0).getWarningInformation());
    }

    @Test
    public void testMessageWithoutDescription() {
        // Arrange
        Message message = createBasicMessage();
        message.getDescs().clear(); // Remove descriptions
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Act - Should not throw exception
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle message without description", dataset);
        NavwarnPreamble preamble = findPreamble(dataset);
        assertTrue("Should have no titles", preamble.getNavwarnTitles().isEmpty());
        assertTrue("Should have no localities", preamble.getLocalities().isEmpty());
    }

    @Test
    public void testGeometryConversionErrorHandling() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);

        // Create an invalid geometry (empty feature collection)
        part.setGeometry(new org.niord.core.geojson.FeatureCollection());
        message.getParts().add(part);

        // Act - Should not throw exception
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle empty geometry", dataset);
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        assertTrue("Should have no geometries", parts.get(0).getGeometries().isEmpty());
    }

    @Test
    public void testMessageWithEmptyShortId() {
        // Arrange
        Message message = createBasicMessage();
        message.setShortId(""); // Empty short ID
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Act - Should use numeric ID instead
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle empty short ID", dataset);
        NavwarnPreamble preamble = findPreamble(dataset);
        String mrn = preamble.getMessageSeriesIdentifier().getInteroperabilityIdentifier();
        assertTrue("Should use numeric ID when short ID is empty", mrn.contains("123"));
    }

    @Test
    public void testMessageWithNullShortId() {
        // Arrange
        Message message = createBasicMessage();
        message.setShortId(null); // Null short ID
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Act - Should use numeric ID instead
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle null short ID", dataset);
        NavwarnPreamble preamble = findPreamble(dataset);
        String mrn = preamble.getMessageSeriesIdentifier().getInteroperabilityIdentifier();
        assertTrue("Should use numeric ID when short ID is null", mrn.contains("123"));
    }

    @Test
    public void testHtmlContentHandling() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);

        // Add HTML content to part description
        MessagePartDesc desc = part.getDesc("en");
        desc.setDetails("<p>This is <strong>HTML</strong> content with <em>formatting</em>.</p>");

        message.getParts().add(part);

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        String text = parts.get(0).getWarningInformation().getInformations().get(0).getText();

        assertFalse("HTML tags should be removed", text.contains("<p>"));
        assertFalse("HTML tags should be removed", text.contains("<strong>"));
        assertTrue("Text content should be preserved", text.contains("This is HTML content"));
    }

    @Test
    public void testDateRangeWithNullDates() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);

        // Create date interval with null dates
        DateInterval dateInterval = part.getEventDates().get(0);
        dateInterval.setFromDate(null);
        dateInterval.setToDate(null);

        message.getParts().add(part);

        // Act - Should not throw exception
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle null dates in date range", dataset);
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        dk.dma.niord.s100.xmlbindings.s124.v2_0_0.FixedDateRangeType dateRange = parts.get(0).getFixedDateRanges().get(0);

        assertNull("Date start should be null", dateRange.getDateStart());
        assertNull("Date end should be null", dateRange.getDateEnd());
    }

    @Test
    public void testUnsupportedGeometryType() {
        // This test would require creating an unsupported geometry type
        // In practice, the GeometryConverter handles standard JTS geometries
        // and throws UnsupportedOperationException for unknown types

        // For now, we test that the mapper handles empty geometries gracefully
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(null); // Null geometry
        message.getParts().add(part);

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        assertNotNull("Should handle null geometry", dataset);
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        assertTrue("Should have no geometries", parts.get(0).getGeometries().isEmpty());
    }

    @Test
    public void testLanguageCodeNormalization() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);

        // Test with Danish language
        Dataset dataset = S124Mapper.map(datasetInfo, message, "da");

        // The language should be normalized correctly
        // This is tested more thoroughly in the basic mapping tests
        assertNotNull("Should handle different language codes", dataset);
    }

    @Test
    public void testPartWithoutBoundingBox() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        part.setGeometry(null); // No geometry, so no bounding box
        message.getParts().add(part);

        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);

        // Assert
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        // The bounding box calculation should handle null geometry gracefully
        // The actual behavior depends on the S124DatasetBuilder implementation
        assertNotNull("Should handle part without geometry", dataset);
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
}