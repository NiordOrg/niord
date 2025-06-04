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

import dk.dma.baleen.s100.xmlbindings.s124.v2_0_0.*;
import org.junit.Test;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;
import org.niord.model.message.Type;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.*;

/**
 * Tests for basic S-124 mapping functionality
 */
public class S124BasicMappingTest extends S124TestBase {
    
    @Test
    public void testBasicMessageMapping() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        assertNotNull("Dataset should not be null", dataset);
        assertNotNull("Dataset ID should not be null", dataset.getId());
        assertEquals("Dataset ID should match", datasetInfo.getDatasetId(), dataset.getId());
        
        // Verify dataset identification
        assertNotNull(dataset.getDatasetIdentificationInformation());
        assertEquals("Dataset encoding specification should match", "S100 Part 10b", dataset.getDatasetIdentificationInformation().getEncodingSpecification());
        assertEquals("Dataset encoding specification edition should match", "2.0.0", dataset.getDatasetIdentificationInformation().getEncodingSpecificationEdition());
        
        // Verify members
        assertNotNull(dataset.getMembers());
        assertFalse(dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements().isEmpty());
    }
    
    @Test
    public void testNavwarnPreambleMapping() {
        // Arrange
        Message message = createBasicMessage();
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        NavwarnPreamble preamble = findPreamble(dataset);
        assertNotNull("Preamble should be present", preamble);
        
        // Verify basic attributes
        assertNotNull("Preamble ID should not be null", preamble.getId());
        assertTrue("Preamble ID should contain country code", preamble.getId().contains("DK"));
        
        // Verify message series identifier
        assertNotNull("Message series identifier should not be null", preamble.getMessageSeriesIdentifier());
        assertEquals("Country name should match", TEST_COUNTRY, preamble.getMessageSeriesIdentifier().getCountryName());
        assertEquals("Production agency should match", TEST_PRODUCTION_AGENCY, preamble.getMessageSeriesIdentifier().getAgencyResponsibleForProduction());
        assertEquals("Warning number should match", 42, preamble.getMessageSeriesIdentifier().getWarningNumber());
        
        // Verify publication time
        assertNotNull(preamble.getPublicationTime());
        
        // Verify warning type
        assertNotNull(preamble.getMessageSeriesIdentifier().getWarningType());
        assertEquals("Warning type should match", WarningTypeLabel.COASTAL_NAVIGATIONAL_WARNING, preamble.getMessageSeriesIdentifier().getWarningType().getValue());
        
        // Verify intService flag
        assertTrue(preamble.isIntService());
    }
    
    @Test
    public void testLocalWarningIntServiceFlag() {
        // Arrange
        Message message = createBasicMessage();
        message.setType(Type.LOCAL_WARNING);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        NavwarnPreamble preamble = findPreamble(dataset);
        assertFalse("International service should be false for local warnings", preamble.isIntService());
    }
    
    @Test
    public void testNavwarnPartMapping() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part1 = createBasicMessagePart(1);
        MessagePart part2 = createBasicMessagePart(2);
        message.getParts().add(part1);
        message.getParts().add(part2);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        assertEquals("Should have 2 NavwarnParts", 2, parts.size());
        
        NavwarnPart firstPart = parts.get(0);
        assertNotNull(firstPart.getId());
        assertTrue("First part ID should contain index", firstPart.getId().contains(".1"));
        
        // Verify header reference
        assertNotNull("Part should have header reference", firstPart.getHeader());
        assertNotNull("Header reference should have href", firstPart.getHeader().getHref());
        assertTrue("Header href should start with #", firstPart.getHeader().getHref().startsWith("#"));
        
        // Verify warning information
        assertNotNull("Part should have warning information", firstPart.getWarningInformation());
        assertNotNull("Should have information", firstPart.getWarningInformation().getInformation());
        assertEquals("Headline should match", "Test warning part 1", 
                firstPart.getWarningInformation().getInformation().getHeadline());
        assertEquals("Text should match", "Details for test warning part 1", firstPart.getWarningInformation().getInformation().getText());
    }
    
    @Test
    public void testFixedDateRangeMapping() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertFalse("Part should have fixed date ranges", navwarnPart.getFixedDateRanges().isEmpty());
        FixedDateRangeType dateRange = navwarnPart.getFixedDateRanges().get(0);
        
        assertNotNull("Date range should have start date", dateRange.getDateStart());
        assertNotNull("Date range should have end date", dateRange.getDateEnd());
        
        // Verify dates
        LocalDate expectedStart = LocalDate.of(2024, 2, 1);
        LocalDate expectedEnd = LocalDate.of(2024, 3, 1);
        
        assertEquals("Start date should match", expectedStart, dateRange.getDateStart().getDate());
        assertEquals("End date should match", expectedEnd, dateRange.getDateEnd().getDate());
    }
    
    @Test
    public void testMultiLanguageTitleMapping() {
        // Arrange
        Message message = createBasicMessage();
        addMultiLanguageDescriptions(message);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        NavwarnPreamble preamble = findPreamble(dataset);
        assertEquals("Should have titles for all languages", 3, preamble.getNavwarnTitles().size());
        
        // Verify languages are present
        var languages = preamble.getNavwarnTitles().stream()
                .map(NavwarnTitleType::getLanguage)
                .toList();
        assertTrue("Should have English title", languages.contains("en"));
        assertTrue("Should have Danish title", languages.contains("da"));
        assertTrue("Should have German title", languages.contains("de"));
    }
    
    @Test
    public void testLanguageSpecificMapping() {
        // Arrange
        Message message = createBasicMessage();
        addMultiLanguageDescriptions(message);
        MessagePart part = createBasicMessagePart(1);
        
        // Add Danish description to part
        var dkDesc = new org.niord.core.message.MessagePartDesc();
        dkDesc.setLang("da");
        dkDesc.setSubject("Dansk emne");
        dkDesc.setDetails("Danske detaljer");
        part.getDescs().add(dkDesc);
        
        message.getParts().add(part);
        
        // Act - Map with Danish language preference
        Dataset dataset = S124Mapper.map(datasetInfo, message, "da");
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertEquals("Language should be Danish", "da", navwarnPart.getWarningInformation().getInformation().getLanguage());
        assertEquals("Headline should be in Danish", "Dansk emne", navwarnPart.getWarningInformation().getInformation().getHeadline());
        assertEquals("Text should be in Danish", "Danske detaljer", navwarnPart.getWarningInformation().getInformation().getText());
    }
    
    @Test
    public void testLanguageFallbackToEnglish() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        // Act - Request German language but only English available
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        var parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertEquals("Language should fallback to English", "en", navwarnPart.getWarningInformation().getInformation().getLanguage());
        assertEquals("Headline should be in English", "Test warning part 1", navwarnPart.getWarningInformation().getInformation().getHeadline());
    }
    
    @Test
    public void testCancellationDateMapping() {
        // Arrange
        Message message = createBasicMessage();
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        NavwarnPreamble preamble = findPreamble(dataset);
        assertNotNull("Should have cancellation date", preamble.getCancellationDate());
        
        // Verify the date matches the publish-to date
        var expectedDate = message.getPublishDateTo().toInstant()
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
        assertEquals("Cancellation date should match", expectedDate, preamble.getCancellationDate());
    }
    
    @Test
    public void testMrnGeneration() {
        // Arrange
        Message message = createBasicMessage();
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        NavwarnPreamble preamble = findPreamble(dataset);
        String mrn = preamble.getMessageSeriesIdentifier().getWarningIdentifier();
        
        assertValidMrn(mrn);
        assertTrue("MRN should contain lowercase short ID", mrn.contains("dk-001-24"));
        assertTrue("MRN should contain navigation warning prefix", mrn.contains(":nw:"));
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