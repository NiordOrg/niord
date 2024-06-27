/*
 * Copyright (c) 2008 Kasper Nielsen.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.niord.s124madame.stuff;

import static org.niord.model.message.MainType.NW;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.Message;
import org.niord.core.message.MessageDesc;
import org.niord.core.message.MessagePart;
import org.niord.core.message.Reference;
import org.niord.model.message.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import _int.iho.s124._1.Dataset;
import _int.iho.s124._1.Dataset.Members;
import _int.iho.s124._1.MessageSeriesIdentifierType;
import _int.iho.s124._1.NAVWARNPart;
import _int.iho.s124._1.NAVWARNPreamble;
import _int.iho.s124._1.NAVWARNTitleType;
import _int.iho.s124._1.NavwarnTypeGeneralType;
import _int.iho.s124._1.References;
import _int.iho.s124._1.WarningTypeLabel;
import _int.iho.s124._1.WarningTypeType;
import _int.iho.s124._1.impl.DatasetImpl;
import _int.iho.s124.s100.gml.base._5_0.DataSetIdentificationType;
import _int.iho.s124.s100.gml.base._5_0.impl.DataSetIdentificationTypeImpl;
import _int.iho.s124.s100.gml.profiles._5_0.BoundingShapeType;
import _int.iho.s124.s100.gml.profiles._5_0.EnvelopeType;
import _int.iho.s124.s100.gml.profiles._5_0.Pos;
import _int.iho.s124.s100.gml.profiles._5_0.ReferenceType;
import _int.iho.s124.s100.gml.profiles._5_0.impl.BoundingShapeTypeImpl;
import _int.iho.s124.s100.gml.profiles._5_0.impl.EnvelopeTypeImpl;
import _int.iho.s124.s100.gml.profiles._5_0.impl.PosImpl;

/**
 *
 */
public class MyMapper {

    private static final net.opengis.gml._3.ObjectFactory gmlObjectFactory = new net.opengis.gml._3.ObjectFactory();
    private static final _int.iho.s125.s100.gml.base._5_0.ObjectFactory s100ObjectFactory = new _int.iho.s125.s100.gml.base._5_0.ObjectFactory();
    private static final _int.iho.s124._1.ObjectFactory s124ObjectFactory = new _int.iho.s124._1.ObjectFactory();

    _int.iho.s124.s100.gml.profiles._5_0.ObjectFactory profileFactory = new _int.iho.s124.s100.gml.profiles._5_0.ObjectFactory();

    private int nextGeomId = 1;

    String lang;

    String country = "DK";

    String productionAgency = "Danish Maritime Authorities";

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * For easy generation of the bounding shapes for the Dataset or individual features, we are using this function.
     *
     * @param atonNodes
     *            The AtoN nodes to generate the bounding shape from
     * @return the bounding shape
     */
    protected BoundingShapeType generateBoundingShape(Message message) {
        // First let us see if we have a bounding shape
        double[] bbox = GeoJsonUtils.computeBBox(message.toGeoJson());
        if (bbox == null) {
            return null;
        }

        Pos lowerCorner = new PosImpl();
        lowerCorner.setValue(new Double[] { bbox[1], bbox[0] });
        Pos upperCorner = new PosImpl();
        upperCorner.setValue(new Double[] { bbox[3], bbox[2] });

        // And create the bounding by envelope
        BoundingShapeType boundingShapeType = new BoundingShapeTypeImpl();
        EnvelopeType envelopeType = new EnvelopeTypeImpl();
        envelopeType.setSrsName("EPSG:4326");
        envelopeType.setLowerCorner(lowerCorner);
        envelopeType.setUpperCorner(upperCorner);
        boundingShapeType.setEnvelope(envelopeType);

        // Finally, return the result
        return boundingShapeType;
    }

    private String htmlToPlainText(String html) {
        return Jsoup.parse(html).text();
    }

    private boolean isHtml(String string) {
        return string.contains("<") || string.contains("&");
    }

    private String lang(String lang) {
        switch (lang.toLowerCase()) {
        case "da":
            return "dan";
        case "en":
        default:
            return "eng";
        }
    }

    public Dataset map0(S124DatasetInfo dataset, Message message) {
        Dataset ds = new DatasetImpl();
        ds.setId(dataset.getDatasetId());

        // ====================================================================//
        // BOUNDED BY SECTION //
        // ====================================================================//
        ds.setBoundedBy(generateBoundingShape(message));

        // ====================================================================//
        // Dataset IDENTIFICATION SECTION //
        // ====================================================================//
        DataSetIdentificationType datasetIdentificationType = new DataSetIdentificationTypeImpl();
        datasetIdentificationType.setEncodingSpecification(dataset.getEncodingSpecification());
        datasetIdentificationType.setEncodingSpecificationEdition(dataset.getEncodingSpecificationEdition());
        datasetIdentificationType.setProductIdentifier(dataset.getProductionIdentifier());
        datasetIdentificationType.setProductEdition(dataset.getProductionEdition());
        datasetIdentificationType.setDatasetFileIdentifier(dataset.getFileIdentifier());
        datasetIdentificationType.setDatasetTitle(dataset.getTitle());
        datasetIdentificationType.setDatasetReferenceDate(LocalDate.now());
        datasetIdentificationType.setDatasetLanguage(dataset.getLanguage());
        datasetIdentificationType.setDatasetAbstract(dataset.getAbstractText());
        ds.setDatasetIdentificationInformation(datasetIdentificationType);

        Members members = s124ObjectFactory.createDatasetMembers();
        ds.setMembers(members);

        NAVWARNPreamble preamble = toNAVWARNPreamble(message);
        members.getNAVWARNPreamblesAndReferencesAndNAVWARNParts().add(preamble);

        List<NAVWARNPart> parts = getParts(message);

        parts.forEach(p -> {
            members.getNAVWARNPreamblesAndReferencesAndNAVWARNParts().add(p);
            ReferenceType rt = profileFactory.createReferenceType();
            rt.setHref("#" + preamble.getId());
            //Reres124ObjectFactory.createFeatureReferenceType();
            p.setHeader(rt);
        });

        List<References> references = toReferences(message);

//        p.getTheReferences().
        // References

        return ds;
    }

    private List<NAVWARNPart> getParts(Message message) {
        List<NAVWARNPart> parts = new ArrayList<>();
        for (MessagePart p : message.getParts()) {
            FeatureCollection fc = p.getGeometry();
            if (fc != null && !fc.getFeatures().isEmpty()) {
                NAVWARNPart part = s124ObjectFactory.createNAVWARNPart();

//                ReferencesType
//                parts.add(part);
            }
        }
        return parts;
    }

    private String nextGeomId(String id) {
        return String.format("G.%s.%d", id, nextGeomId++);
    }

    private String toMessageId(Message msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(country).append(".");
        if (msg.getShortId() != null) {
            sb.append(msg.getShortId());
        } else {
            sb.append(msg.getId());
        }
        return sb.toString();
    }

    private MessageSeriesIdentifierType toMessageSeriesIdentifierType(Message message) {
        MessageSeriesIdentifierType messageSeriesIdentifer = s124ObjectFactory.createMessageSeriesIdentifierType();

        int refYear = LocalDate.ofInstant(message.getPublishDateFrom().toInstant(), ZoneId.systemDefault()).getYear();
        messageSeriesIdentifer.setYear(BigInteger.valueOf(refYear));
        messageSeriesIdentifer.setCountryName(country);
        messageSeriesIdentifer.setAgencyResponsibleForProduction(productionAgency);
        messageSeriesIdentifer.setNameOfSeries(message.getMessageSeries().getSeriesId());

        messageSeriesIdentifer.setWarningIdentifier(toMrn(message));
        messageSeriesIdentifer.setWarningNumber(BigInteger.valueOf(message.getNumber()));
        messageSeriesIdentifer.setWarningType(toWarningTypeType(message.getType()));

        return messageSeriesIdentifer;
    }

    private String toMrn(Message msg) {
        String internalId = msg.getShortId() != null ? msg.getShortId() : msg.getId().toString();
        return "urn:mrn:iho:" + msg.getMainType().name().toLowerCase() + ":dk:" + internalId.toLowerCase();
    }

    private NAVWARNPreamble toNAVWARNPreamble(Message msg) {
        NAVWARNPreamble p = s124ObjectFactory.createNAVWARNPreamble();

        p.setId(toMessageId(msg));

        // TODO, All but local warning?
        p.setIntService(false);

        p.setMessageSeriesIdentifier(toMessageSeriesIdentifierType(msg));

        MessageDesc md = msg.getDesc(lang);
        if (md != null && !StringUtils.isBlank(md.getTitle())) {
            NAVWARNTitleType titleType = s124ObjectFactory.createNAVWARNTitleType();
            titleType.setLanguage(lang(md.getLang()));
            titleType.setText(md.getTitle());
            p.setNAVWARNTitle(titleType);
        }

        NavwarnTypeGeneralType ngt = s124ObjectFactory.createNavwarnTypeGeneralType();
        ngt.setCode("AAA");
        ngt.setValue("BBB");
        p.setNavwarnTypeGeneral(ngt);

        // Dates
        if (msg.getPublishDateTo() != null) {
            p.setCancellationDate(toLocalDateTime(msg.getPublishDateTo()));
        }
        p.setPublicationTime(toLocalDateTime(msg.getPublishDateFrom()));

//        final int warningNumber = msg.getNumber() != null ? msg.getNumber() : -1;
//        final int year = msg.getYear() != null ? msg.getYear() % 100 : 0;
//        final Type type = msg.getType();
//
//        MessageSeriesIdentifierType messageSeriesIdentifierType = createMessageSeries(type, warningNumber, year, mrn, lang);
//
//        nwPreambleType.setMessageSeriesIdentifier(messageSeriesIdentifierType);
//        nwPreambleType.setId("PR." + id);

        // ---

        // ---

        // Set publication time

        return p;
    }

    private String toPlainText(String string) {
        return isHtml(string) ? htmlToPlainText(string) : string;
    }

    private List<References> toReferences(Message message) {
        List<References> result = new ArrayList<>();
        List<Reference> references = message.getReferences();
        if (references != null) {
            for (Reference r : references) {
                Message refMessage = r.getMessage();
                if (refMessage.getMainType() == NW) {
                    References gmlreferences = s124ObjectFactory.createReferences();
                    gmlreferences.setId(String.format(toMessageId(refMessage)));
                    gmlreferences.setNoMessageOnHand(false);
                    gmlreferences.getMessageSeriesIdentifiers().add(toMessageSeriesIdentifierType(refMessage));
                    result.add(gmlreferences);
                }
            }
        }
        return result;
    }

    private WarningTypeType toWarningTypeType(Type type) {
        WarningTypeType wtt = s124ObjectFactory.createWarningTypeType();

        switch (type) {
        case LOCAL_WARNING:
            wtt.setValue(WarningTypeLabel.LOCAL_NAVIGATIONAL_WARNING);
            wtt.setCode(BigInteger.valueOf(1));
            break;
        case COASTAL_WARNING:
            wtt.setValue(WarningTypeLabel.COASTAL_NAVIGATIONAL_WARNING);
            wtt.setCode(BigInteger.valueOf(2));
            break;
        case SUBAREA_WARNING:
            wtt.setValue(WarningTypeLabel.SUB_AREA_NAVIGATIONAL_WARNING);
            wtt.setCode(BigInteger.valueOf(3));
            break;
        case NAVAREA_WARNING:
            wtt.setValue(WarningTypeLabel.NAVAREA_NAVIGATIONAL_WARNING);
            wtt.setCode(BigInteger.valueOf(4));
            break;
        default:
            log.warn("Messages of type {} not mapped.", type.name());
        }
        return wtt;
    }

    public static Dataset map(S124DatasetInfo dataset, Message message) {
        return new MyMapper().map0(dataset, message);
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        Instant instant = date.toInstant();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

    }
}
