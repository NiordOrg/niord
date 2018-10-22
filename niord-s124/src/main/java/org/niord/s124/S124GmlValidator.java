package org.niord.s124;

import org.slf4j.Logger;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@ApplicationScoped
public class S124GmlValidator {

    private Validator validator;
    private Logger log;

    public S124GmlValidator() {
        // CDI
    }

    @Inject
    public S124GmlValidator(Logger log) {
        this.log = log;

        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            this.log.debug("Searching for S-124 XSD schema");
            URL schemaResource = this.getClass().getResource("/xsd/S124/1.0/20180910/S124.xsd");
            this.log.info("Loading S-124 XSD schema");
            Schema schema = schemaFactory.newSchema(schemaResource);
            this.log.debug("Creating S-124 XSD schema validator");
            validator = schema.newValidator();
            this.log.info("Created S-124 XSD schema validator");
        } catch (SAXException e) {
            this.log.error(e.getMessage());
        }
    }

    public List<ValidationError> validateAgainstSchema(JAXBElement<?> jaxbElement) throws JAXBException {
        requireNonNull(jaxbElement);
        requireNonNull(validator);

        JAXBContext jaxbContext = JAXBContext.newInstance(jaxbElement.getValue().getClass());
        JAXBSource source = new JAXBSource(jaxbContext, jaxbElement);

        List<ValidationError> validationErrors = new LinkedList<>();
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
                validationErrors.add(new ValidationError("WARNING", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }

            @Override
            public void fatalError(SAXParseException e) {
                validationErrors.add(new ValidationError("FATAL", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }

            @Override
            public void error(SAXParseException e) {
                validationErrors.add(new ValidationError("ERROR", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }
        });

        try {
            validator.validate(source);
        } catch (SAXException e) {
            validationErrors.add(new ValidationError("UNKNOWN", e.getMessage(), null, null));
        } catch (IOException e) {
            validationErrors.add(new ValidationError("IO", e.getMessage(), null, null));
        }

        return validationErrors;
    }

    public void printXml(JAXBElement<?> jaxbElement, OutputStream out) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(jaxbElement.getValue().getClass());
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(jaxbElement, out);
    }

    public static class ValidationError {
        ValidationError(String type, String message, Integer lineNumber, Integer columnNumber) {
            this.type = type;
            this.message = message;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public Integer getColumnNumber() {
            return columnNumber;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();

            sb.append(type);
            sb.append(": ");
            sb.append("Line ");
            sb.append(lineNumber != null ? lineNumber : -1);
            sb.append(", column ");
            sb.append(columnNumber != null ? columnNumber : -1);
            sb.append(": ");
            sb.append(message);

            return sb.toString();
        }

        private final String type;
        private final String message;
        private final Integer lineNumber;
        private final Integer columnNumber;
    }

}
