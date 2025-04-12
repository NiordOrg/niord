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
package org.niord.s100.s124;

import static org.niord.model.message.MainType.NW;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.DateInterval;
import org.niord.core.message.Message;
import org.niord.core.message.MessageDesc;
import org.niord.core.message.MessagePart;
import org.niord.core.message.MessagePartDesc;
import org.niord.core.message.Reference;
import org.niord.core.model.DescEntity;
import org.niord.model.message.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.CurveProperty;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.DataSetIdentificationType;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.PointProperty;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.S100SpatialAttributeType;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.SurfaceProperty;
import dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.impl.DataSetIdentificationTypeImpl;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.BoundingShapeType;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.EnvelopeType;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.Pos;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.ReferenceType;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.impl.BoundingShapeTypeImpl;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.impl.EnvelopeTypeImpl;
import dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.impl.PosImpl;
import dk.dma.baleen.s100.xmlbindings.s124.v2_0_0.*;
import dk.dma.baleen.s100.xmlbindings.s124.v2_0_0.impl.DatasetImpl;

/**
 *
 */
public class S124Mapper {

    private static final net.opengis.gml._3.ObjectFactory gmlObjectFactory = new net.opengis.gml._3.ObjectFactory();
    private static final dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.ObjectFactory s100ObjectFactory = new dk.dma.baleen.s100.xmlbindings.s100.gml.base._5_0.ObjectFactory();
    private static final dk.dma.baleen.s100.xmlbindings.s124.v2_0_0.ObjectFactory s124ObjectFactory = new dk.dma.baleen.s100.xmlbindings.s124.v2_0_0.ObjectFactory();

    String country = "DK";

    String lang = "en";

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private int nextGeomId = 1;

    String productionAgency = "Danish Maritime Authorities";

    dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.ObjectFactory profileFactory = new dk.dma.baleen.s100.xmlbindings.s100.gml.profiles._5_0.ObjectFactory();

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
            return "da";
        case "en":
        default:
            return "en";
        }
    }

    private Dataset map0(S124DatasetInfo dataset, Message message) {
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

        Dataset.Members members = s124ObjectFactory.createDatasetMembers();
        ds.setMembers(members);

        NavwarnPreamble preamble = toDataModelNAVWARNPreamble(message);
        members.getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements().add(preamble);

        List<NavwarnPart> parts = new ArrayList<>();
        for (MessagePart p : message.getParts()) {
            parts.add(toDataModelNAVWARNPart(message, p));
        }

        parts.forEach(p -> {
            members.getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements().add(p);
            ReferenceType rt = profileFactory.createReferenceType();
            rt.setHref("#" + preamble.getId());
            // Reres124ObjectFactory.createFeatureReferenceType();
            p.setHeader(rt);
        });

        List<References> references = toDataModelReferences(message);

//        p.getTheReferences().
        // References

        return ds;
    }

    private String nextGeomId(String id) {
        return String.format("G.%s.%d", id, nextGeomId++);
    }

    private boolean showDesc(DescEntity<?> de) {
        return de.getLang().equals("en");
    }

    private NavwarnPart toDataModelNAVWARNPart(Message message, MessagePart messagePart) {
        NavwarnPart part = s124ObjectFactory.createNavwarnPart();

        // From AbstractGMLType
        part.setId(toMrn(message) + "." + messagePart.getIndexNo());

        // From AbstractFeatureType
        part.setBoundedBy(S124DatasetBuilder.generateBoundingShape(messagePart));

        /********************************* Complex Attributes *********************************/

        /************ featureName: featureName[0..*] ************/

        /************ featureReference: featureReference [0..*] ************/

        /************ fixedDateRange: fixedDateRange [0..*] ************/

        for (DateInterval di : messagePart.getEventDates()) {
            FixedDateRangeType fdrt = toComplexFixedDateRange(di);
            part.getFixedDateRanges().add(fdrt);
        }

        /************ warningInformation: WarningInformationType ************/
        WarningInformationType wit = s124ObjectFactory.createWarningInformationType();

        StringBuilder sb = new StringBuilder();

        for (MessagePartDesc mpd : messagePart.getDescs()) {
            sb.append("lan: ").append(mpd.getLang()).append("\n");
            sb.append("sub: ").append(mpd.getSubject()).append("\n");
            sb.append("det: ").append(mpd.getDetails()).append("\n");
        }

        MessagePartDesc mpd = messagePart.getDesc("en");

        InformationType it = s124ObjectFactory.createInformationType();
        it.setLanguage(mpd.getLang());
        it.setHeadline(mpd.getSubject());
        it.setText(htmlToPlainText(mpd.getDetails()));
        wit.setInformation(it);

        part.setWarningInformation(wit);

        /********************************* Spatial Attributes *********************************/

        /************ geometry: Point, Curve, Surface [0..*] (ordered} ************/
        FeatureCollection fc = messagePart.getGeometry();
        if (fc != null && !fc.getFeatures().isEmpty()) {
            for (Feature f : fc.getFeatures()) {
                List<S100SpatialAttributeType> l = new GeometryS124Converter().geometryToS124PointCurveSurfaceGeometry(f.getGeometry());
                for (S100SpatialAttributeType s : l) {
                    NavwarnPart.Geometry geo = s124ObjectFactory.createNavwarnPartGeometry();
                    switch (s) {
                    case SurfaceProperty sp -> geo.setSurfaceProperty(sp);
                    case PointProperty sp -> geo.setPointProperty(sp);
                    case CurveProperty sp -> geo.setCurveProperty(sp);
                    default -> throw new UnsupportedOperationException("Cannot deal with " + s);
                    }
                    part.getGeometries().add(geo);
                }

            }
        }

        /********************************* Simple Attributes *********************************/

        /************ restriction: NAVWARNPartRestrictionType 0..1] ************/
        part.setRestriction(null);

        return part;
    }

    private NavwarnPreamble toDataModelNAVWARNPreamble(Message msg) {
        NavwarnPreamble p = s124ObjectFactory.createNavwarnPreamble();

        // From AbstractGMLType
        p.setId(toMessageId(msg));

        /********************************* Complex Types *********************************/

        /************ affectedChartPublications: affectedChartPublications [0..*) ************/

//        p.getAffectedChartPublications()
        /************ generalArea: generalArea [1..*] (ordered) ************/

//        if (msg.getAreas() != null) {
//            for (Area a : msg.getAreas()) {
//                a.getChildren()t
//            }
//        }
//        msg.getAreas();
//
//        p.getGeneralAreas().

        /************ locality: locality [0..*] (ordered} ************/

        // p.getLocalities()

        /************ messageSeriesidentifier: messageSeriesidentifier ************/
        p.setMessageSeriesIdentifier(toMessageSeriesIdentifierType(msg));

        /************ NAVWARNtitle: NAVWARNtitle [O..*] ************/
        MessageDesc md = msg.getDesc(lang);
        if (md != null && !StringUtils.isBlank(md.getTitle())) {
            NavwarnTitleType titleType = s124ObjectFactory.createNavwarnTitleType();
            titleType.setLanguage(lang(md.getLang()));
            titleType.setText(md.getTitle());
            p.getNavwarnTitles().add(titleType);
        }

        /********************************* Simple Attributes *********************************/

        /************ cancellationDate: dateTime 0..1] ************/
        if (msg.getPublishDateTo() != null) {
            p.setCancellationDate(toOtherOffsetDateTime(msg.getPublishDateTo()));
        }

        /************ intService: Boolean ************/
        // TODO, All but local warning?
        p.setIntService(false);

        /************ navwarnTypeGeneral: navwarnTypeGeneral ************/
//        NavwarnTypeGeneralType ngt = s124ObjectFactory.createNavwarnTypeGeneralType();
//        ngt.setCode("AAA");
//        ngt.setValue("BBB");
//        p.setNavwarnTypeGeneral(ngt);

        /************ publicationTime: dateTime ************/
        p.setPublicationTime(toOtherOffsetDateTime(msg.getPublishDateFrom()));
        return p;
    }

    private List<References> toDataModelReferences(Message message) {
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
        messageSeriesIdentifer.setYear(refYear);
        messageSeriesIdentifer.setCountryName(country);
        messageSeriesIdentifer.setAgencyResponsibleForProduction(productionAgency);

        // TODO maybe we need a proper name for the message series
        messageSeriesIdentifer.setNameOfSeries(message.getMessageSeries().getSeriesId());

        messageSeriesIdentifer.setWarningIdentifier(toMrn(message));
        messageSeriesIdentifer.setWarningNumber(message.getNumber());
        messageSeriesIdentifer.setWarningType(toComplexTypeWarningTypeType(message.getType()));

        return messageSeriesIdentifer;
    }

    private String toOtherPlainText(String string) {
        return isHtml(string) ? htmlToPlainText(string) : string;
    }

    private FixedDateRangeType toComplexFixedDateRange(DateInterval type) {
        FixedDateRangeType result = s124ObjectFactory.createFixedDateRangeType();

        // We only exports the actual dates, so we ignore type.getAllDay.
        Date fromDate = type.getFromDate();
        Date toDate = type.getFromDate();

        if (fromDate != null) {
            DateStartType dst = s124ObjectFactory.createDateStartType();
            dst.setDate(fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            result.setDateStart(dst);
        }

        if (toDate != null) {
            DateEndType det = s124ObjectFactory.createDateEndType();
            det.setDate(toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            result.setDateEnd(det);
        }
        return result;
    }

    private WarningTypeType toComplexTypeWarningTypeType(Type type) {
        WarningTypeType result = s124ObjectFactory.createWarningTypeType();
        switch (type) {
        case LOCAL_WARNING:
            result.setValue(WarningTypeLabel.LOCAL_NAVIGATIONAL_WARNING);
            result.setCode(BigInteger.valueOf(1));
            break;
        case COASTAL_WARNING:
            result.setValue(WarningTypeLabel.COASTAL_NAVIGATIONAL_WARNING);
            result.setCode(BigInteger.valueOf(2));
            break;
        case SUBAREA_WARNING:
            result.setValue(WarningTypeLabel.SUB_AREA_NAVIGATIONAL_WARNING);
            result.setCode(BigInteger.valueOf(3));
            break;
        case NAVAREA_WARNING:
            result.setValue(WarningTypeLabel.NAVAREA_NAVIGATIONAL_WARNING);
            result.setCode(BigInteger.valueOf(4));
            break;
        default:
            log.warn("Messages of type {} not mapped.", type.name());
        }
        return result;
    }

    static Dataset map(S124DatasetInfo dataset, Message message) {
        return new S124Mapper().map0(dataset, message);
    }

    private static String toMrn(Message msg) {
        String internalId = msg.getShortId() != null ? msg.getShortId() : msg.getId().toString();
        return "urn:mrn:iho:" + msg.getMainType().name().toLowerCase() + ":dk:" + internalId.toLowerCase();
    }

    private static OffsetDateTime toOtherOffsetDateTime(Date date) {
        Instant instant = date.toInstant();
        return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
    private static LocalDateTime toOtherLocalDateTime(Date date) {
        Instant instant = date.toInstant();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

// Dates

//final int warningNumber = msg.getNumber() != null ? msg.getNumber() : -1;
//final int year = msg.getYear() != null ? msg.getYear() % 100 : 0;
//final Type type = msg.getType();
//
//MessageSeriesIdentifierType messageSeriesIdentifierType = createMessageSeries(type, warningNumber, year, mrn, lang);
//
//nwPreambleType.setMessageSeriesIdentifier(messageSeriesIdentifierType);
//nwPreambleType.setId("PR." + id);

// ---

// ---

// Set publication time
