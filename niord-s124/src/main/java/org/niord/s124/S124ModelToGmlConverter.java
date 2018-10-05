package org.niord.s124;

import _int.iho.s124.gml.cs0._0.DatasetType;
import _int.iho.s124.gml.cs0._0.ObjectFactory;
import org.niord.core.message.Message;

import javax.inject.Inject;
import javax.xml.bind.*;
import java.io.StringWriter;

public class S124ModelToGmlConverter {

    private final ObjectFactory objectFactory;

    @Inject
    public S124ModelToGmlConverter(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    public JAXBElement<DatasetType> toS124(Message message) {
        DatasetType datasetType = new DatasetType();
        datasetType.setId("myId");

        JAXBElement<DatasetType> dataSet = objectFactory.createDataSet(datasetType);
        return dataSet;
    }

    public String toString(JAXBElement element) {
        StringWriter sw = new StringWriter();

        try {
            JAXBContext context = JAXBContext.newInstance(element.getValue().getClass());
            Marshaller mar = context.createMarshaller();
            mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            mar.marshal(element, sw);
            return sw.toString();
        } catch (PropertyException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
