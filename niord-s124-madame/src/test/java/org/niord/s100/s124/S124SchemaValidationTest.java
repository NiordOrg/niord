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
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.Test;
import org.niord.core.area.Area;
import org.niord.core.category.Category;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;
import org.niord.core.message.Reference;
import org.niord.model.message.ReferenceType;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.Dataset;

/**
 * Validates generated S-124 datasets against the S-124 2.0.0 XSD that ships with the bindings.
 * <p>
 * The mapping tests elsewhere assert individual fields; this one checks the property that actually matters to a
 * consumer, namely that the whole document is schema valid. It is what catches the class of defect where a Niord short
 * id such as {@code "Local Warning-120-26"} is used verbatim as a {@code gml:id} even though gml:id is an
 * {@code NCName} and admits neither spaces nor colons.
 */
public class S124SchemaValidationTest extends S124TestBase {

    /** The S-124 schema, resolved from the bindings jar; sibling imports resolve relative to it. */
    private static final String S124_XSD = "/xsd/124_2.0.0.xsd";

    private List<String> schemaErrors(Dataset dataset) throws Exception {
        String xml = S124Utils.marshalS124(dataset);
        URL xsd = getClass().getResource(S124_XSD);
        assertTrue("The S-124 XSD should ship with the bindings", xsd != null);

        List<String> errors = new ArrayList<>();
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = factory.newSchema(xsd);
        javax.xml.validation.Validator validator = schema.newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) {
                // not interesting here
            }

            public void error(SAXParseException e) {
                errors.add(e.getMessage());
            }

            public void fatalError(SAXParseException e) {
                errors.add(e.getMessage());
            }
        });
        validator.validate(new StreamSource(new StringReader(xml)));
        return new ArrayList<>(new LinkedHashSet<>(errors));
    }

    private void assertSchemaValid(Message message) throws Exception {
        S124DatasetInfo info = new S124DatasetInfo(message.getShortId(), "Danish Maritime Authority", "DMA", List.of(message));
        List<String> errors = schemaErrors(S124Mapper.map(info, message));
        assertEquals("Generated S-124 should be schema valid but was: " + errors, List.of(), errors);
    }

    /** Gives a message the area and category that a published navigational warning carries. */
    private Message enrich(Message message) {
        Area area = createTestArea("the-sound", "The Sound");
        message.getAreas().add(area);
        Category category = createTestCategory("firing exercises");
        message.getCategories().add(category);
        MessagePart part = createBasicMessagePart(1);
        message.getParts().add(part);
        return message;
    }

    @Test
    public void testRealisticWarningIsSchemaValid() throws Exception {
        assertSchemaValid(enrich(createBasicMessage()));
    }

    /**
     * Niord short ids contain a space, which is not admissible in a gml:id.
     */
    @Test
    public void testShortIdWithSpaceIsSchemaValid() throws Exception {
        Message message = enrich(createBasicMessage());
        message.setShortId("Local Warning-120-26");
        assertSchemaValid(message);
    }

    /**
     * A colon is what makes a URN unusable as a gml:id, so an id shaped like one has to be reduced as well.
     */
    @Test
    public void testShortIdWithColonsIsSchemaValid() throws Exception {
        Message message = enrich(createBasicMessage());
        message.setShortId("urn:mrn:iho:nw:dk:test:001");
        assertSchemaValid(message);
    }

    /**
     * An NCName may not start with a digit.
     */
    @Test
    public void testShortIdStartingWithDigitIsSchemaValid() throws Exception {
        Message message = enrich(createBasicMessage());
        message.setShortId("123-warning");
        assertSchemaValid(message);
    }

    /**
     * Danish letters are legal in an NCName and should survive.
     */
    @Test
    public void testDanishShortIdIsSchemaValid() throws Exception {
        Message message = enrich(createBasicMessage());
        message.setShortId("Kystadvarsel Æøå-7-26");
        assertSchemaValid(message);
    }

    /**
     * generalArea and navwarnTypeGeneral are both mandatory, so a message carrying neither an area nor a category
     * still has to produce a schema valid dataset.
     */
    @Test
    public void testMessageWithoutAreaOrCategoryIsSchemaValid() throws Exception {
        Message message = createBasicMessage();
        message.getParts().add(createBasicMessagePart(1));
        assertSchemaValid(message);
    }

    /**
     * References carry two mandatory children, referenceCategory and theWarning, that a plain mapping run does not
     * otherwise produce.
     */
    @Test
    public void testMessageWithReferenceIsSchemaValid() throws Exception {
        Message message = enrich(createBasicMessage());
        message.setShortId("Local Warning-120-26");

        Message referenced = enrich(createBasicMessage());
        referenced.setId(999);
        referenced.setShortId("Local Warning-119-26");

        Reference reference = new Reference();
        reference.setMessage(referenced);
        reference.setType(ReferenceType.CANCELLATION);
        message.getReferences().add(reference);

        assertSchemaValid(message);
    }

    /**
     * A plain reference is not a cancellation, so it has to carry the other reference category.
     */
    @Test
    public void testPlainReferenceIsSchemaValid() throws Exception {
        Message message = enrich(createBasicMessage());
        Message referenced = enrich(createBasicMessage());
        referenced.setId(998);
        referenced.setShortId("Local Warning-118-26");

        Reference reference = new Reference();
        reference.setMessage(referenced);
        reference.setType(ReferenceType.REFERENCE);
        message.getReferences().add(reference);

        assertSchemaValid(message);
    }

    /**
     * nameOfSeries, year and publicationTime are all mandatory, but a message need not carry a message series or a
     * publication date - a preview of a VERIFIED message does not.
     */
    @Test
    public void testMessageWithoutSeriesOrPublishDateIsSchemaValid() throws Exception {
        Message message = enrich(createBasicMessage());
        message.setMessageSeries(null);
        message.setPublishDateFrom(null);
        assertSchemaValid(message);
    }

    /**
     * Two references to the same message must not produce two elements carrying the same gml:id.
     */
    @Test
    public void testDuplicateReferencesDoNotCollide() throws Exception {
        Message message = enrich(createBasicMessage());
        Message referenced = enrich(createBasicMessage());
        referenced.setId(997);
        referenced.setShortId("Local Warning-117-26");

        for (ReferenceType type : new ReferenceType[] { ReferenceType.REFERENCE, ReferenceType.UPDATE }) {
            Reference reference = new Reference();
            reference.setMessage(referenced);
            reference.setType(type);
            message.getReferences().add(reference);
        }

        assertSchemaValid(message);
    }

    /**
     * The MRN goes into interoperabilityIdentifier, where a space would be just as invalid as in a gml:id.
     */
    @Test
    public void testInteroperabilityIdentifierHasNoSpace() throws Exception {
        Message message = enrich(createBasicMessage());
        message.setShortId("Local Warning-120-26");
        S124DatasetInfo info = new S124DatasetInfo(message.getShortId(), "Danish Maritime Authority", "DMA", List.of(message));
        Dataset dataset = S124Mapper.map(info, message);

        String mrn = findPreamble(dataset).getMessageSeriesIdentifier().getInteroperabilityIdentifier();
        assertTrue("MRN should be an MRN: " + mrn, mrn.startsWith("urn:mrn:iho:"));
        assertTrue("MRN should not contain a space: " + mrn, !mrn.contains(" "));
    }

    private dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPreamble findPreamble(Dataset dataset) {
        return dataset.getMembers().getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements().stream()
                .filter(dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPreamble.class::isInstance)
                .map(dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPreamble.class::cast).findFirst().orElse(null);
    }
}
