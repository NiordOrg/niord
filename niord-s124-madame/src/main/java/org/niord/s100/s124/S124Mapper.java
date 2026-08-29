/*
 * Copyright 2017 Danish Maritime Authority.
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

import static org.niord.model.message.MainType.NW;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.niord.core.area.Area;
import org.niord.core.area.AreaDesc;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.DateInterval;
import org.niord.core.message.Message;
import org.niord.core.message.MessageDesc;
import org.niord.core.message.MessagePart;
import org.niord.core.message.MessagePartDesc;
import org.niord.core.message.MessageTag;
import org.niord.core.message.Reference;
import org.niord.model.message.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.CurveProperty;
import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.DataSetIdentificationType;
import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.DatasetPurposeType;
import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.MDTopicCategoryCode;
import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.PointProperty;
import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.S100SpatialAttributeType;
import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.SurfaceProperty;
import dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.impl.DataSetIdentificationTypeImpl;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.BoundingShapeType;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.EnvelopeType;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.Pos;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.ReferenceType;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.impl.BoundingShapeTypeImpl;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.impl.EnvelopeTypeImpl;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.impl.PosImpl;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.AffectedChartPublicationsType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.ChartAffectedType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.Dataset;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.DateEndType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.DateStartType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.FixedDateRangeType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.GeneralAreaType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.InformationType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.LocalityType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.LocationNameType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.MessageSeriesIdentifierType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPart;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnPreamble;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnTitleType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnTypeGeneralLabel;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.NavwarnTypeGeneralType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.ReferenceCategoryLabel;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.ReferenceCategoryType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.References;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.RestrictionLabel;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.RestrictionType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.WarningInformationType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.WarningTypeLabel;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.WarningTypeType;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.impl.DatasetImpl;
import dk.dma.niord.s100.xmlbindings.s124.v2_0_0.util.S124CodedValues;

/**
 * Maps from a Niord {@link Message} to a S-124 message
 */
class S124Mapper {

    private static final net.opengis.gml._3.ObjectFactory gmlObjectFactory = new net.opengis.gml._3.ObjectFactory();
    private static final dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.ObjectFactory s100ObjectFactory = new dk.dma.niord.s100.xmlbindings.s100.gml.base._5_0.ObjectFactory();
    private static final dk.dma.niord.s100.xmlbindings.s124.v2_0_0.ObjectFactory s124ObjectFactory = new dk.dma.niord.s100.xmlbindings.s124.v2_0_0.ObjectFactory();

    /** S-100 Part 10b Table 10b-4: application profile of a base dataset. */
    private static final String APPLICATION_PROFILE_BASE = "1";

    String country = "DK";

    String lang = "en";

    public S124Mapper() {
        this("en");
    }

    public S124Mapper(String lang) {
        this.lang = lang;
    }

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private int nextGeomId = 1;

    // S-124 clause 4.3.3: agencyResponsibleForProduction takes the agency's S-62 producer code from the IHO GI
    // Registry, not the agency's name
    String productionAgency = S124Identifiers.PRODUCER_CODE;

    dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.ObjectFactory profileFactory = new dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.ObjectFactory();

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
        // bbox is [minLon, minLat, maxLon, maxLat], but GML expects [lat, lon]
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
        case "de":
            return "de";
        case "en":
        default:
            return "en";
        }
    }

    private Dataset map0(S124DatasetInfo dataset, Message message) {
        // Validate input parameters
        if (dataset == null) {
            throw new IllegalArgumentException("Dataset info cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (message.getMainType() != NW) {
            throw new IllegalArgumentException("Only navigational warning messages (NW) are supported");
        }

        Dataset ds = new DatasetImpl();
        // gml:id is of XSD type ID, so the raw dataset id (a Niord short id such as "Local Warning-120-26") has to be
        // reduced to an NCName first
        ds.setId(S124Identifiers.toNCName(dataset.getDatasetId()));

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
        // S-100 Part 10b Table 10b-4 defines "1" for base datasets and "2" for update datasets, and pairs the value
        // with datasetPurpose below
        datasetIdentificationType.setApplicationProfile(APPLICATION_PROFILE_BASE);
        datasetIdentificationType.setDatasetFileIdentifier(dataset.getFileIdentifier());
        datasetIdentificationType.setDatasetTitle(dataset.getTitle());
        datasetIdentificationType.setDatasetReferenceDate(LocalDate.now());
        datasetIdentificationType.setDatasetLanguage(dataset.getLanguage());
        datasetIdentificationType.setDatasetAbstract(dataset.getAbstractText());
        // datasetTopicCategory is mandatory (minOccurs=1); navigational warnings are ocean related
        datasetIdentificationType.getDatasetTopicCategories().add(MDTopicCategoryCode.OCEANS);
        datasetIdentificationType.setDatasetPurpose(DatasetPurposeType.BASE);
        // updateNumber is mandatory and is 0 for a base dataset
        datasetIdentificationType.setUpdateNumber(BigInteger.ZERO);

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
            rt.setRole("header");
            p.setHeader(rt);
        });

        // Add references to other messages
        List<References> references = toDataModelReferences(message);
        for (References ref : references) {
            members.getNavwarnPartsAndNavwarnAreaAffectedsAndTextPlacements().add(ref);
        }

        // Also add references to the preamble if this message references others
        if (!references.isEmpty()) {
            for (References ref : references) {
                ReferenceType rt = profileFactory.createReferenceType();
                rt.setHref("#" + ref.getId());
                rt.setRole("reference");
                preamble.getTheReferences().add(rt);
            }
        }

        return ds;
    }



    private NavwarnPart toDataModelNAVWARNPart(Message message, MessagePart messagePart) {
        NavwarnPart part = s124ObjectFactory.createNavwarnPart();

        // From AbstractGMLType. The MRN cannot be used here: gml:id is an NCName and an MRN contains colons.
        part.setId(toMessageId(message) + "." + messagePart.getIndexNo());

        // From AbstractFeatureType
        part.setBoundedBy(S124DatasetBuilder.generateBoundingShape(messagePart));

        /********************************* Complex Attributes *********************************/

        /************ featureName: featureName[0..*] ************/

        /************ featureReference: featureReference [0..*] ************/
        // Add references to other messages that this part references
        if (message.getReferences() != null) {
            for (Reference ref : message.getReferences()) {
                if (ref.getMessage() != null && ref.getMessage().getMainType() == NW) {
                    ReferenceType rt = profileFactory.createReferenceType();
                    rt.setHref("#" + toMessageId(ref.getMessage()));
                    rt.setRole(ref.getType() != null ? ref.getType().name() : "reference");
                    part.getAffects().add(rt);
                }
            }
        }

        /************ fixedDateRange: fixedDateRange [0..*] ************/

        for (DateInterval di : messagePart.getEventDates()) {
            FixedDateRangeType fdrt = toComplexFixedDateRange(di);
            part.getFixedDateRanges().add(fdrt);
        }

        /************ warningInformation: WarningInformationType ************/
        WarningInformationType wit = s124ObjectFactory.createWarningInformationType();

        MessagePartDesc mpd = messagePart.getDesc(lang);
        if (mpd == null) {
            // Fallback to English if preferred language not available
            mpd = messagePart.getDesc("en");
        }

        if (mpd != null) {
            InformationType it = s124ObjectFactory.createInformationType();
            it.setLanguage(lang(mpd.getLang()));
            it.setText(htmlToPlainText(mpd.getDetails()));
            wit.getInformations().add(it);
        }

        part.setWarningInformation(wit);

        /********************************* Spatial Attributes *********************************/

        /************ geometry: Point, Curve, Surface [0..*] (ordered} ************/
        FeatureCollection fc = messagePart.getGeometry();
        if (fc != null && !fc.getFeatures().isEmpty()) {
            for (Feature f : fc.getFeatures()) {
                List<S100SpatialAttributeType> l = new GeometryS124Converter().geometryToS124PointCurveSurfaceGeometry(f.getGeometry());
                for (S100SpatialAttributeType s : l) {
                    NavwarnPart.Geometry geo = s124ObjectFactory.createNavwarnPartGeometry();
                    // Assign geometry ID
                    String geomId = nextGeomId(part.getId());

                    switch (s) {
                    case SurfaceProperty sp -> {
                        if (sp.getSurface() != null) {
                            sp.getSurface().setId(geomId);
                        }
                        geo.setSurfaceProperty(sp);
                    }
                    case PointProperty pp -> {
                        if (pp.getPoint() != null) {
                            pp.getPoint().setId(geomId);
                        }
                        geo.setPointProperty(pp);
                    }
                    case CurveProperty cp -> {
                        if (cp.getCurve() != null) {
                            cp.getCurve().setId(geomId);
                        }
                        geo.setCurveProperty(cp);
                    }
                    default -> throw new UnsupportedOperationException("Cannot deal with " + s);
                    }
                    part.getGeometries().add(geo);
                }

            }
        }

        /********************************* Simple Attributes *********************************/

        /************ restriction: NAVWARNPartRestrictionType 0..1] ************/
        // Set restriction based on message categories or tags
        part.setRestriction(toRestrictionType(message));

        return part;
    }

    private NavwarnPreamble toDataModelNAVWARNPreamble(Message msg) {
        NavwarnPreamble p = s124ObjectFactory.createNavwarnPreamble();

        // From AbstractGMLType
        p.setId(toMessageId(msg));

        /********************************* Complex Types *********************************/

        /************ affectedChartPublications: affectedChartPublications [0..*) ************/
        if (msg.getCharts() != null && !msg.getCharts().isEmpty()) {
            for (Chart chart : msg.getCharts()) {
                AffectedChartPublicationsType acpt = toAffectedChartPublicationType(chart);
                if (acpt != null) {
                    p.getAffectedChartPublications().add(acpt);
                }
            }
        }
        /************ generalArea: generalArea [1..*] (ordered) ************/
        if (msg.getAreas() != null && !msg.getAreas().isEmpty()) {
            for (Area area : msg.getAreas()) {
                GeneralAreaType gat = toGeneralAreaType(area);
                if (gat != null) {
                    p.getGeneralAreas().add(gat);
                }
            }
        }
        if (p.getGeneralAreas().isEmpty()) {
            // generalArea is mandatory, so a message that carries no area would otherwise produce a dataset that the
            // S-124 schema rejects. Fall back to the vicinity, and failing that to the nationality.
            MessageDesc md = msg.getDesc(lang);
            String fallback = (md != null && StringUtils.isNotBlank(md.getVicinity())) ? md.getVicinity() : country;
            log.warn("Message {} has no area; falling back to generalArea \"{}\"", msg.getShortId(), fallback);
            GeneralAreaType gat = s124ObjectFactory.createGeneralAreaType();
            LocationNameType locationName = s124ObjectFactory.createLocationNameType();
            locationName.setLanguage(lang(lang));
            locationName.setText(fallback);
            gat.getLocationNames().add(locationName);
            p.getGeneralAreas().add(gat);
        }

        /************ locality: locality [0..*] (ordered} ************/
        MessageDesc mdVicinity = msg.getDesc(lang);
        if (mdVicinity != null && StringUtils.isNotBlank(mdVicinity.getVicinity())) {
            LocalityType locality = s124ObjectFactory.createLocalityType();
            LocationNameType locationName = s124ObjectFactory.createLocationNameType();
            locationName.setLanguage(lang(mdVicinity.getLang()));
            locationName.setText(mdVicinity.getVicinity());
            locality.getLocationNames().add(locationName);
            p.getLocalities().add(locality);
        }

        /************ messageSeriesidentifier: messageSeriesidentifier ************/
        p.setMessageSeriesIdentifier(toMessageSeriesIdentifierType(msg));

        /************ NAVWARNtitle: NAVWARNtitle [O..*] ************/
        // Add titles for all available languages
        for (MessageDesc desc : msg.getDescs()) {
            if (desc != null && !StringUtils.isBlank(desc.getTitle())) {
                NavwarnTitleType titleType = s124ObjectFactory.createNavwarnTitleType();
                titleType.setLanguage(lang(desc.getLang()));
                titleType.setText(desc.getTitle());
                p.getNavwarnTitles().add(titleType);
            }
        }

        /********************************* Simple Attributes *********************************/

        /************ cancellationDate: dateTime 0..1] ************/
        if (msg.getPublishDateTo() != null) {
            p.setCancellationDate(toOtherOffsetDateTime(msg.getPublishDateTo()));
        }

        /************ intService: Boolean ************/
        // International service is true for all except local warnings
        p.setIntService(msg.getType() != Type.LOCAL_WARNING);

        /************ navwarnTypeGeneral: navwarnTypeGeneral ************/
        NavwarnTypeGeneralType ngt = toNavwarnTypeGeneral(msg);
        if (ngt == null) {
            // navwarnTypeGeneral is mandatory. A message without a mappable category gets the same treatment as one
            // whose category is not recognised.
            log.warn("Message {} has no mappable category; falling back to navwarnTypeGeneral \"Other Hazards\"", msg.getShortId());
            ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.OTHER_HAZARDS);
        }
        p.setNavwarnTypeGeneral(ngt);

        /************ publicationTime: dateTime ************/
        // publicationTime is mandatory, so an unpublished message falls back to its audit dates
        p.setPublicationTime(toOtherOffsetDateTime(referenceDate(msg)));
        return p;
    }

    private List<References> toDataModelReferences(Message message) {
        List<References> result = new ArrayList<>();
        List<Reference> references = message.getReferences();
        // A message can reference the same message more than once (say a repetition and an update). The id is derived
        // from the referenced message, so emitting both would put a duplicate gml:id in the dataset.
        Set<String> emitted = new HashSet<>();
        if (references != null) {
            for (Reference r : references) {
                Message refMessage = r.getMessage();
                if (refMessage != null && refMessage.getMainType() == NW) {
                    String referenceId = "R." + toMessageId(refMessage);
                    if (!emitted.add(referenceId)) {
                        log.warn("Message {} references {} more than once; keeping only the first", message.getShortId(),
                                refMessage.getShortId());
                        continue;
                    }
                    References gmlreferences = s124ObjectFactory.createReferences();
                    gmlreferences.setId(referenceId);
                    gmlreferences.setNoMessageOnHand(false);

                    // Add reference type information if available
                    ReferenceCategoryLabel category = ReferenceCategoryLabel.WARNING_REFERENCE;
                    if (r.getType() != null) {
                        switch (r.getType()) {
                            case CANCELLATION:
                                // This reference cancels the referenced message
                                gmlreferences.setNoMessageOnHand(true);
                                category = ReferenceCategoryLabel.WARNING_CANCELLATION;
                                break;
                            case UPDATE:
                            case REPETITION:
                            case REFERENCE:
                            default:
                                gmlreferences.setNoMessageOnHand(false);
                                break;
                        }
                    }

                    // referenceCategory is mandatory
                    ReferenceCategoryType referenceCategory = s124ObjectFactory.createReferenceCategoryType();
                    referenceCategory.setValue(category);
                    referenceCategory.setCode(S124CodedValues.codeOf(category));
                    gmlreferences.setReferenceCategory(referenceCategory);

                    // theWarning is mandatory. The referenced warning lives in its own dataset, so it is addressed by
                    // its MRN rather than by a local "#id". Clause 10b-9 wants an association to carry a role.
                    ReferenceType theWarning = profileFactory.createReferenceType();
                    theWarning.setHref(toMrn(refMessage));
                    theWarning.setRole("theWarning");
                    gmlreferences.setTheWarning(theWarning);

                    gmlreferences.getMessageSeriesIdentifiers().add(toMessageSeriesIdentifierType(refMessage));
                    result.add(gmlreferences);
                }
            }
        }
        return result;
    }

    /**
     * Builds the {@code gml:id} identifying a message inside a dataset. The result is an NCName, because gml:id is of
     * XSD type ID and Niord short ids such as "Local Warning-120-26" contain spaces.
     */
    private String toMessageId(Message msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(country).append(".");
        if (msg.getShortId() != null && !msg.getShortId().trim().isEmpty()) {
            sb.append(msg.getShortId());
        } else {
            sb.append(msg.getId());
        }
        return S124Identifiers.toNCName(sb.toString());
    }

    /**
     * Builds a messageSeriesIdentifier. Every child except interoperabilityIdentifier and nationality is mandatory,
     * so each one falls back to the best value available rather than being left out.
     */
    private MessageSeriesIdentifierType toMessageSeriesIdentifierType(Message message) {
        MessageSeriesIdentifierType messageSeriesIdentifer = s124ObjectFactory.createMessageSeriesIdentifierType();

        // year is mandatory
        Date reference = referenceDate(message);
        messageSeriesIdentifer.setYear(LocalDate.ofInstant(reference.toInstant(), ZoneId.systemDefault()).getYear());

        messageSeriesIdentifer.setNationality(country);
        messageSeriesIdentifer.setAgencyResponsibleForProduction(productionAgency);

        // nameOfSeries is mandatory
        String nameOfSeries = message.getMessageSeries() != null ? message.getMessageSeries().getSeriesId() : null;
        if (StringUtils.isBlank(nameOfSeries)) {
            nameOfSeries = country + "-" + message.getMainType().name();
            log.warn("Message {} has no message series; falling back to nameOfSeries \"{}\"", message.getShortId(), nameOfSeries);
        }
        messageSeriesIdentifer.setNameOfSeries(nameOfSeries);

        messageSeriesIdentifer.setInteroperabilityIdentifier(toMrn(message));

        // warningNumber is mandatory; 0 is the placeholder a message that has not been numbered yet gets
        messageSeriesIdentifer.setWarningNumber(message.getNumber() != null ? message.getNumber() : 0);

        if (message.getType() != null) {
            messageSeriesIdentifer.setWarningType(toComplexTypeWarningTypeType(message.getType()));
        }

        return messageSeriesIdentifer;
    }

    /**
     * The date a message should be dated by: its publication date if it has one, else the best audit date available.
     */
    private static Date referenceDate(Message message) {
        if (message.getPublishDateFrom() != null) {
            return message.getPublishDateFrom();
        }
        if (message.getUpdated() != null) {
            return message.getUpdated();
        }
        return message.getCreated() != null ? message.getCreated() : new Date();
    }


    private FixedDateRangeType toComplexFixedDateRange(DateInterval type) {
        FixedDateRangeType result = s124ObjectFactory.createFixedDateRangeType();

        // We only exports the actual dates, so we ignore type.getAllDay.
        Date fromDate = type.getFromDate();
        Date toDate = type.getToDate();

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
        WarningTypeLabel label;
        switch (type) {
        case LOCAL_WARNING:
            label = WarningTypeLabel.LOCAL_NAVIGATIONAL_WARNING;
            break;
        case COASTAL_WARNING:
            label = WarningTypeLabel.COASTAL_NAVIGATIONAL_WARNING;
            break;
        case SUBAREA_WARNING:
            label = WarningTypeLabel.SUB_AREA_NAVIGATIONAL_WARNING;
            break;
        case NAVAREA_WARNING:
            label = WarningTypeLabel.NAVAREA_NAVIGATIONAL_WARNING;
            break;
        default:
            // warningType is mandatory and its content is an enumeration, so returning an empty element here would
            // only produce invalid XML further down. map0 already restricts us to NW messages, whose four types are
            // all handled above.
            throw new IllegalArgumentException("Messages of type " + type.name() + " cannot be mapped to an S-124 warning type");
        }
        result.setValue(label);
        result.setCode(S124CodedValues.codeOf(label));
        return result;
    }

    static Dataset map(S124DatasetInfo dataset, Message message) {
        return new S124Mapper().map0(dataset, message);
    }

    static Dataset map(S124DatasetInfo dataset, Message message, String language) {
        return new S124Mapper(language).map0(dataset, message);
    }

    private String nextGeomId(String id) {
        return String.format("G.%s.%d", id, nextGeomId++);
    }

    /**
     * Pairs a navwarnTypeGeneral label with its code. The code is always derived from the label via
     * {@link S124CodedValues#codeOf}, so the two can never drift apart.
     */
    private NavwarnTypeGeneralType navwarnTypeGeneral(NavwarnTypeGeneralLabel label) {
        NavwarnTypeGeneralType ngt = s124ObjectFactory.createNavwarnTypeGeneralType();
        ngt.setValue(label);
        ngt.setCode(S124CodedValues.codeOf(label));
        return ngt;
    }

    private NavwarnTypeGeneralType toNavwarnTypeGeneral(Message message) {
        // Map based on message categories
        if (message.getCategories() != null && !message.getCategories().isEmpty()) {
            Category primaryCategory = message.getCategories().get(0);

            // Map category name to appropriate value based on actual Niord category structure
            // TODO: These are hardcoded to what we have defined in NiordDK.
            // A generic solution would probably be to setup the S-124 Category type when
            // configuring categories on the Sysadmin page
            String categoryName = getCategoryName(primaryCategory);
            if (categoryName != null) {
                NavwarnTypeGeneralType ngt;
                switch (categoryName.toLowerCase()) {
                    case "light":
                    case "light buoy":
                    case "buoy":
                    case "beacon":
                        // Aids to navigation changes
                        ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.AIDS_TO_NAVIGATION_CHANGES);
                        break;
                    case "drifting object":
                        // Drifting hazards
                        ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.DRIFTING_HAZARDS);
                        break;
                    case "radio navigation":
                        // Communication or broadcast service changes (closest match for radio navigation issues)
                        ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.COMMUNICATION_OR_BROADCAST_SERVICE_CHANGE);
                        break;
                    case "firing exercises":
                        // Special operations
                        ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.SPECIAL_OPERATIONS);
                        break;
                    case "obstruction":
                        // Check specific templates for more granular mapping
                        String templateType = getObstructionTemplateType(message);
                        if ("wreck".equals(templateType)) {
                            ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.DANGEROUS_NATURAL_PHENOMENA);
                        } else if ("uncharted obstruction".equals(templateType) ||
                                   "reduced depth".equals(templateType)) {
                            ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.NEWLY_DISCOVERED_DANGERS);
                        } else {
                            // Other underwater operations
                            ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.SPECIAL_OPERATIONS);
                        }
                        break;
                    case "ports":
                    default:
                        // Other hazards
                        ngt = navwarnTypeGeneral(NavwarnTypeGeneralLabel.OTHER_HAZARDS);
                        break;
                }
                return ngt;
            }
        }
        return null;
    }

    private String getCategoryName(Category category) {
        // Get English description name, fallback to legacy ID
        if (category.getDescs() != null) {
            for (var desc : category.getDescs()) {
                if ("en".equals(desc.getLang()) && desc.getName() != null) {
                    return desc.getName();
                }
            }
        }
        return category.getLegacyId();
    }

    private String getObstructionTemplateType(Message message) {
        // Try to determine the specific obstruction type from message parts
        if (message.getParts() != null && !message.getParts().isEmpty()) {
            MessagePart firstPart = message.getParts().get(0);
            MessagePartDesc desc = firstPart.getDesc("en");
            if (desc != null && desc.getSubject() != null) {
                String subject = desc.getSubject().toLowerCase();
                if (subject.contains("wreck")) {
                    return "wreck";
                } else if (subject.contains("uncharted") || subject.contains("obstruction")) {
                    return "uncharted obstruction";
                } else if (subject.contains("depth") || subject.contains("reduced")) {
                    return "reduced depth";
                }
            }
        }
        return null;
    }

    private GeneralAreaType toGeneralAreaType(Area area) {
        if (area != null) {
            GeneralAreaType gat = s124ObjectFactory.createGeneralAreaType();

            // Get the area description for the current language
            AreaDesc desc = area.getDesc(lang);
            LocationNameType locationName = s124ObjectFactory.createLocationNameType();
            if (desc != null && StringUtils.isNotBlank(desc.getName())) {
                locationName.setText(desc.getName());
                locationName.setLanguage(lang(desc.getLang()));
            } else {
                // Fallback to area ID if no description
                locationName.setText(area.getId().toString());
                locationName.setLanguage(lang);
            }
            gat.getLocationNames().add(locationName);

            // Set geometry if available
            if (area.getGeometry() != null) {
                // Convert area geometry to S-124 format
                List<S100SpatialAttributeType> geoms = new GeometryS124Converter()
                        .geometryToS124PointCurveSurfaceGeometry(area.getGeometry());
                if (!geoms.isEmpty()) {
                    // For now, just use the first geometry
                    // In a full implementation, you might need to handle multiple geometries
                }
            }

            return gat;
        }
        return null;
    }

    private AffectedChartPublicationsType toAffectedChartPublicationType(Chart chart) {
        if (chart != null) {
            AffectedChartPublicationsType acpt = s124ObjectFactory.createAffectedChartPublicationsType();

            // Set chart number
            if (StringUtils.isNotBlank(chart.getChartNumber())) {
                ChartAffectedType chartAffected = s124ObjectFactory.createChartAffectedType();
                chartAffected.setChartNumber(chart.getChartNumber());
                acpt.setChartAffected(chartAffected);
            }

            // Set publication date if available
            // Note: Chart entity doesn't have a published date field
            // This could be added later if needed

            return acpt;
        }
        return null;
    }

    /**
     * Pairs a restriction label with its code, derived via {@link S124CodedValues#codeOf}.
     */
    private RestrictionType restriction(RestrictionLabel label) {
        RestrictionType restriction = s124ObjectFactory.createRestrictionType();
        restriction.setValue(label);
        restriction.setCode(S124CodedValues.codeOf(label));
        return restriction;
    }

    private RestrictionType toRestrictionType(Message message) {
        // Check message tags or categories for restriction information
        if (message.getTags() != null) {
            for (MessageTag tag : message.getTags()) {
                if ("RESTRICTED".equalsIgnoreCase(tag.getName())) {
                    return restriction(RestrictionLabel.ENTRY_PROHIBITED);
                } else if ("CAUTION".equalsIgnoreCase(tag.getName())) {
                    return restriction(RestrictionLabel.ENTRY_RESTRICTED);
                }
            }
        }
        return null;
    }

    /**
     * Builds the MRN identifying a message. This is used for interoperabilityIdentifier only - an MRN contains colons
     * and can therefore never serve as a {@code gml:id}.
     */
    private static String toMrn(Message msg) {
        String internalId = (msg.getShortId() != null && !msg.getShortId().trim().isEmpty())
            ? msg.getShortId() : msg.getId().toString();
        // A space is not admissible anywhere in a URI, so the short id has to be reduced to an MRN-safe segment
        return "urn:mrn:iho:" + msg.getMainType().name().toLowerCase() + ":dk:" + S124Identifiers.toMrnSegment(internalId);
    }

    private static OffsetDateTime toOtherOffsetDateTime(Date date) {
        Instant instant = date.toInstant();
        return OffsetDateTime.ofInstant(instant, ZoneId.of("UTC"));
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
