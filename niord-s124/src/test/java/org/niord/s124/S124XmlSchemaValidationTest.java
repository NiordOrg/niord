package org.niord.s124;

import _int.iho.s124.gml.cs0._0.DatasetType;
import _int.iho.s124.gml.cs0._0.ObjectFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class S124XmlSchemaValidationTest {

    private final ObjectFactory objectFactory = new ObjectFactory();

    private static Validator validator;

    @BeforeClass
    public static void before() throws SAXException {
        Schema mySchema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new File("src/main/resources/xsd/S124/1.0/20180910/S124.xsd"));
        validator = mySchema.newValidator();
    }

    @Test
    public void testMinimalDataSet() throws JAXBException, IOException, SAXException {
        JAXBElement<DatasetType> datasetType = objectFactory.createDataSet(new DatasetType());

        datasetType.getValue().setId("IDTBS");
        validateSchema(datasetType);
    }

    private void validateSchema(JAXBElement<?> jaxbElement) throws JAXBException, IOException, SAXException {
        JAXBContext jaxbContext = JAXBContext.newInstance(jaxbElement.getValue().getClass());
        JAXBSource source = new JAXBSource(jaxbContext, jaxbElement);

        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                throw new RuntimeException("Schema validaton failed: " + e.getMessage(), e);
            }

            @Override
            public void fatalError(SAXParseException e) {
                throw new RuntimeException("Schema validaton failed: " + e.getMessage(), e);
            }

            @Override
            public void error(SAXParseException e) {
                throw new RuntimeException("Schema validaton failed: " + e.getMessage(), e);
            }
        });

        try {
            validator.validate(source);
            System.out.println("XML Schema validation OK:");
            printXml(jaxbElement, System.out);
        } catch (Throwable t) {
            System.err.println("XML Schema validation failed:");
            printXml(jaxbElement, System.err);
            throw t;
        }
    }

    private void printXml(JAXBElement<?> jaxbElement, OutputStream out) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(jaxbElement.getValue().getClass());
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(jaxbElement, out);
    }

}
