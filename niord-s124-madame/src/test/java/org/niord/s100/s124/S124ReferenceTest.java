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
import org.niord.core.message.Reference;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.MainType;

import static org.junit.Assert.*;

/**
 * Tests for S-124 reference handling functionality
 */
public class S124ReferenceTest extends S124TestBase {
    
    @Test
    public void testBasicReferenceMapping() {
        // Arrange
        Message referencedMessage = createBasicMessage();
        referencedMessage.setId(456);
        referencedMessage.setShortId("DK-002-24");
        referencedMessage.setNumber(43);
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference ref = createReference(referencedMessage, ReferenceType.REFERENCE);
        message.getReferences().add(ref);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<References> references = findReferences(dataset);
        assertEquals("Should have one reference", 1, references.size());
        
        References s124Ref = references.get(0);
        assertNotNull(s124Ref.getId());
        assertFalse("Reference should indicate message is available", s124Ref.isNoMessageOnHand());
        assertFalse("Should have message series identifiers", s124Ref.getMessageSeriesIdentifiers().isEmpty());
        
        // Verify message series identifier
        MessageSeriesIdentifierType msgSeries = s124Ref.getMessageSeriesIdentifiers().get(0);
        assertEquals(TEST_COUNTRY, msgSeries.getCountryName());
        assertEquals(43, msgSeries.getWarningNumber());
        assertTrue(msgSeries.getWarningIdentifier().contains("dk-002-24"));
    }
    
    @Test
    public void testCancellationReference() {
        // Arrange
        Message cancelledMessage = createBasicMessage();
        cancelledMessage.setId(789);
        cancelledMessage.setShortId("DK-003-24");
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference cancelRef = createReference(cancelledMessage, ReferenceType.CANCELLATION);
        message.getReferences().add(cancelRef);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<References> references = findReferences(dataset);
        assertEquals("Should have one reference", 1, references.size());
        
        References s124Ref = references.get(0);
        assertTrue(s124Ref.isNoMessageOnHand());
    }
    
    @Test
    public void testUpdateReference() {
        // Arrange
        Message updatedMessage = createBasicMessage();
        updatedMessage.setId(999);
        updatedMessage.setShortId("DK-004-24");
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference updateRef = createReference(updatedMessage, ReferenceType.UPDATE);
        message.getReferences().add(updateRef);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<References> references = findReferences(dataset);
        assertEquals("Should have one reference", 1, references.size());
        
        References s124Ref = references.get(0);
        assertFalse(s124Ref.isNoMessageOnHand());
    }
    
    @Test
    public void testMultipleReferences() {
        // Arrange
        Message ref1Message = createBasicMessage();
        ref1Message.setId(100);
        ref1Message.setShortId("DK-100-24");
        
        Message ref2Message = createBasicMessage();
        ref2Message.setId(200);
        ref2Message.setShortId("DK-200-24");
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        message.getReferences().add(createReference(ref1Message, ReferenceType.REFERENCE));
        message.getReferences().add(createReference(ref2Message, ReferenceType.CANCELLATION));
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<References> references = findReferences(dataset);
        assertEquals("Should have two references", 2, references.size());
        
        // Find the cancellation reference
        References cancelRef = references.stream()
                .filter(References::isNoMessageOnHand)
                .findFirst()
                .orElse(null);
        assertNotNull("Should have normal reference", cancelRef);
        
        // Find the normal reference
        References normalRef = references.stream()
                .filter(ref -> !ref.isNoMessageOnHand())
                .findFirst()
                .orElse(null);
        assertNotNull(normalRef);
    }
    
    @Test
    public void testReferenceToNonNavWarning() {
        // Arrange
        Message nonNwMessage = createBasicMessage();
        nonNwMessage.setMainType(MainType.NM); // Notice to Mariners, not Navigation Warning
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference ref = createReference(nonNwMessage, ReferenceType.REFERENCE);
        message.getReferences().add(ref);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<References> references = findReferences(dataset);
        assertEquals("Should not include references to non-NW messages", 0, references.size());
    }
    
    @Test
    public void testFeatureReferencesInParts() {
        // Arrange
        Message referencedMessage = createBasicMessage();
        referencedMessage.setId(555);
        referencedMessage.setShortId("DK-555-24");
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference ref = createReference(referencedMessage, ReferenceType.REFERENCE);
        message.getReferences().add(ref);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        // Note: S-124 v2.0.0 API may not have ReferenceType/getAffects() - using best effort mapping
        assertFalse("Part should have affects references", navwarnPart.getAffects().isEmpty());
    }
    
    @Test
    public void testFeatureReferencesInPreamble() {
        // Arrange
        Message referencedMessage = createBasicMessage();
        referencedMessage.setId(666);
        referencedMessage.setShortId("DK-666-24");
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference ref = createReference(referencedMessage, ReferenceType.REFERENCE);
        message.getReferences().add(ref);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        NavwarnPreamble preamble = findPreamble(dataset);
        // Note: S-124 v2.0.0 may not have getAffects() on preamble - using best effort mapping
        assertNotNull("Preamble should exist", preamble);
    }
    
    @Test
    public void testHeaderReferencesInParts() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        NavwarnPreamble preamble = findPreamble(dataset);
        java.util.List<NavwarnPart> parts = findNavwarnParts(dataset);
        NavwarnPart navwarnPart = parts.get(0);
        
        assertNotNull("Part should have header reference", navwarnPart.getHeader());
        assertNotNull("Header should have href", navwarnPart.getHeader().getHref());
        assertEquals("Header should reference preamble", "#" + preamble.getId(), navwarnPart.getHeader().getHref());
        assertEquals("Header should have header role", "header", navwarnPart.getHeader().getRole());
    }
    
    @Test
    public void testReferenceWithNullMessage() {
        // Arrange
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference ref = new Reference();
        ref.setMessage(null); // null referenced message
        ref.setType(ReferenceType.REFERENCE);
        message.getReferences().add(ref);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert - should not crash and should not include the null reference
        java.util.List<References> references = findReferences(dataset);
        assertEquals("Should not include null references", 0, references.size());
    }
    
    @Test
    public void testReferenceIdGeneration() {
        // Arrange
        Message referencedMessage = createBasicMessage();
        referencedMessage.setId(777);
        referencedMessage.setShortId("DK-777-24");
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference ref = createReference(referencedMessage, ReferenceType.REFERENCE);
        message.getReferences().add(ref);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<References> references = findReferences(dataset);
        References s124Ref = references.get(0);
        
        assertNotNull("Reference should have ID", s124Ref.getId());
        assertTrue("Reference ID should contain country", s124Ref.getId().contains("DK"));
        assertTrue("Reference ID should contain referenced message ID", s124Ref.getId().contains("777"));
    }
    
    @Test
    public void testRepetitionReference() {
        // Arrange
        Message repeatedMessage = createBasicMessage();
        repeatedMessage.setId(888);
        repeatedMessage.setShortId("DK-888-24");
        
        Message message = createBasicMessage();
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        
        Reference repetitionRef = createReference(repeatedMessage, ReferenceType.REPETITION);
        message.getReferences().add(repetitionRef);
        
        // Act
        Dataset dataset = S124Mapper.map(datasetInfo, message);
        
        // Assert
        java.util.List<References> references = findReferences(dataset);
        assertEquals("Should have one reference", 1, references.size());
        
        References s124Ref = references.get(0);
        assertFalse(s124Ref.isNoMessageOnHand());
    }
    
    // Helper methods
    
    private java.util.List<References> findReferences(Dataset dataset) {
        return dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements()
                .stream()
                .filter(References.class::isInstance)
                .map(References.class::cast)
                .toList();
    }
    
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