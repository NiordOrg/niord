package org.niord.s124;

import _int.iho.s124.gml.cs0._0.DatasetType;
import _int.iho.s124.gml.cs0._0.ObjectFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class S124ValidatorTest {

    private static S124Validator sut;
    private static ObjectFactory objectFactory;

    @BeforeClass
    public static void before() throws SAXException {
        sut = new S124Validator();
        objectFactory = new ObjectFactory();
    }

    @Test
    public void detectMissingId() throws JAXBException {
        JAXBElement<DatasetType> datasetType = objectFactory.createDataSet(new DatasetType());
        List<S124Validator.ValidationError> validationErrors = sut.validateSchema(datasetType);

        assertTrue(validationErrors.size() == 1);
        assertEquals("ERROR: Line 0, column 0: cvc-complex-type.4: Attribute 'id' must appear on element 'S124:DataSet'.", validationErrors.get(0).toString());
    }

    @Test
    public void minimalValidXmlValidatesOk() throws JAXBException {
        ObjectFactory objectFactory = new ObjectFactory();
        JAXBElement<DatasetType> datasetType = objectFactory.createDataSet(new DatasetType());

        datasetType.getValue().setId("IDTBS");
        List<S124Validator.ValidationError> validationErrors = sut.validateSchema(datasetType);

        assertTrue(validationErrors.isEmpty());
    }

}