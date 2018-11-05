package org.niord.s124;

import _int.iho.s100gml._1.FeatureObjectIdentifier;
import _int.iho.s100gml._1.PointPropertyType;
import _int.iho.s100gml._1.PointType;
import _int.iho.s124.gml.cs0._0.*;
import com.google.common.collect.Lists;
import net.opengis.gml._3.*;
import net.opengis.gml._3.ReferenceType;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.core.util.TimeUtils;
import org.niord.model.geojson.*;
import org.niord.model.message.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.*;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.StringWriter;
import java.lang.Boolean;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.reverse;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.niord.model.message.MainType.NW;

public class S124ModelToGmlConverter {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final net.opengis.gml._3.ObjectFactory gmlObjectFactory;
    private final _int.iho.s100gml._1.ObjectFactory s100ObjectFactory;
    private final _int.iho.s124.gml.cs0._0.ObjectFactory s124ObjectFactory;

    private int nextGeomId = 1;

    private boolean used = false;

    public S124ModelToGmlConverter() {
        this.gmlObjectFactory = new net.opengis.gml._3.ObjectFactory();
        this.s100ObjectFactory = new _int.iho.s100gml._1.ObjectFactory();
        this.s124ObjectFactory = new _int.iho.s124.gml.cs0._0.ObjectFactory();
    }

    public String toString(JAXBElement element) {
        StringWriter sw = new StringWriter();

        try {
            JAXBContext context = JAXBContext.newInstance(element.getValue().getClass());
            Marshaller mar = context.createMarshaller();
            mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            mar.marshal(element, sw);
            return sw.toString();
        } catch (PropertyException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public JAXBElement<DatasetType> toGml(SystemMessageVo msg, FeatureCollectionVo[] geoJson, MessageVo[] referencedMessageVos, String lang) {

        if (used == true)
            throw new IllegalStateException("This instance of " + this.getClass().getSimpleName() + " has already been used.");

        used = true;

        // ---

        final String mrn = toMrn(msg);

        final String internalId = msg.getShortId() != null ? msg.getShortId() : msg.getId();
        final String id = "DK." + internalId;

        // ---

        final DatasetType gml = new DatasetType();

        addEnvelope(gml, geoJson);

        addPreamble(gml, lang, id, mrn, msg);

        if (msg.getParts() != null)
            addFeatureParts(gml, lang, id, mrn, msg.getParts());

        addReferences(gml, lang, msg, referencedMessageVos, mrn);

        // ---

        JAXBElement<DatasetType> dataSet = s124ObjectFactory.createDataSet(gml);

        return dataSet;
    }

    private void addEnvelope(DatasetType datasetType, FeatureCollectionVo[] geoJson) {
        datasetType.setBoundedBy(createBoundingBox(geoJson));
    }

    private void addPreamble(DatasetType datasetType, String lang, String id, String mrn, SystemMessageVo msg) {
        NWPreambleType nwPreambleType = s124ObjectFactory.createNWPreambleType();

        datasetType.setId(id);

        // ---

        final int warningNumber = msg.getNumber() != null ? msg.getNumber() : -1;
        final int year = msg.getYear() != null ? msg.getYear() % 100 : 0;
        final Type type = msg.getType();
        MessageSeriesIdentifierType messageSeriesIdentifierType = createMessageSeries(type, warningNumber, year, mrn, lang);

        nwPreambleType.setMessageSeriesIdentifier(messageSeriesIdentifierType);
        nwPreambleType.setId("PR." + id);

        // ---

        MessageDescVo msgDesc = msg.getDesc(lang);

        if (msgDesc != null && !StringUtils.isBlank(msgDesc.getTitle())) {
            TitleType titleType = s124ObjectFactory.createTitleType();
            titleType.setLanguage(lang(msgDesc.getLang()));
            titleType.setText(msgDesc.getTitle());
            nwPreambleType.getTitle().add(titleType);
        }

        // ---

        GregorianCalendar publicationDate = new GregorianCalendar();
        publicationDate.setTime(msg.getPublishDateFrom());
        try {
            nwPreambleType.setPublicationDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(publicationDate));
        } catch (DatatypeConfigurationException e) {
            log.error(e.getMessage(), e);
        }

        // ---

        msg.getAreas().forEach(area -> {
            GeneralAreaType generalAreaType = createArea(s124ObjectFactory.createGeneralAreaType(), area);
            nwPreambleType.getGeneralArea().add(generalAreaType);

            LocalityType localityType = createLocality(s124ObjectFactory.createLocalityType(), area, lang);
            nwPreambleType.getLocality().add(localityType);
        });

        // ---

        msg.getParts().forEach(part -> {
            ReferenceType referenceType = gmlObjectFactory.createReferenceType();
            referenceType.setHref(String.format("#%s.%d", id, part.getIndexNo() + 1));
            nwPreambleType.getTheWarningPart().add(referenceType);
        });

        // ---

        JAXBElement<NWPreambleType> nwPreambleTypeElement = s124ObjectFactory.createNWPreamble(nwPreambleType);
        IMemberType imember = s124ObjectFactory.createIMemberType();
        imember.setInformationType(nwPreambleTypeElement);
        datasetType.getImemberOrMember().add(imember);
    }

    private void addFeatureParts(DatasetType gml, String lang, String id, String mrn, List<MessagePartVo> parts) {
        parts.forEach(part -> {
            if (part.getGeometry() != null && part.getGeometry().getFeatures() != null && part.getGeometry().getFeatures().length > 0) {
                NavigationalWarningFeaturePartType navigationalWarningFeaturePartType = createNavWarnPart(part, lang, id, mrn);
                JAXBElement<NavigationalWarningFeaturePartType> navigationalWarningFeaturePart = s124ObjectFactory.createNavigationalWarningFeaturePart(navigationalWarningFeaturePartType);
                MemberType memberType = s124ObjectFactory.createMemberType();
                memberType.setAbstractFeature(navigationalWarningFeaturePart);
                gml.getImemberOrMember().add(memberType);
            } else {
                log.error("S124_InformationNoticePart not supported.");
            }
        });
    }

    private void addReferences(DatasetType gml, String lang, SystemMessageVo msg, MessageVo[] referencedMessageVos, String mrn) {
        List<ReferenceVo> referencesVo = msg.getReferences();

        if (referencesVo != null) {
            referencesVo.forEach(referenceVo -> {
                if (referenceVo != null) {

                    // Lookup reference to get more details
                    String referencedMessageId = referenceVo.getMessageId();
                    if (referencedMessageId != null) {
                        Optional<MessageVo> referencedMessageVoOptional = Stream.of(referencedMessageVos).filter(referencedMessageVo -> referencedMessageId.equals(referencedMessageVo.getShortId())).findAny();

                        if (!referencedMessageVoOptional.isPresent()) {
                            log.error("Could not find referenced message {}", referencedMessageId);
                        } else {
                            MessageVo referencedMessageVo = referencedMessageVoOptional.get();

                            final Type refType = referencedMessageVo.getType();
                            final String refMrn = toMrn(referencedMessageVo);
                            final int refWarningNumber = referencedMessageVo.getNumber();
                            final int refYear = TimeUtils.getCalendarField(referencedMessageVo.getPublishDateFrom(), Calendar.YEAR);

                            if (refType.getMainType() == NW) {
                                ReferencesType referencesType = s124ObjectFactory.createReferencesType();
                                referencesType.setId(String.format("REF.DK.%s", referencedMessageId));
                                referencesType.setNoMessageOnHand(false);

                                ReferenceType referenceType = gmlObjectFactory.createReferenceType();
                                referenceType.setHref(String.format("#DK.%s", referencedMessageId));
                                referencesType.setTheWarning(referenceType);

                                MessageSeriesIdentifierType messageSeriesIdentifer = createMessageSeries(refType, refWarningNumber, refYear, refMrn, lang);
                                referencesType.getMessageSeriesIdentifier().add(messageSeriesIdentifer);

                                switch (referenceVo.getType()) {
                                    case CANCELLATION:
                                        referencesType.setReferenceCategory(_int.iho.s124.gml.cs0._0.ReferenceCategoryType.CANCELLATION);
                                        break;
                                    case REPETITION:
                                    case REPETITION_NEW_TIME:
                                        referencesType.setReferenceCategory(_int.iho.s124.gml.cs0._0.ReferenceCategoryType.REPETITION);
                                        break;
                                    case UPDATE:
                                        referencesType.setReferenceCategory(_int.iho.s124.gml.cs0._0.ReferenceCategoryType.UPDATE);
                                        break;
                                    case REFERENCE:
                                        referencesType.setReferenceCategory(_int.iho.s124.gml.cs0._0.ReferenceCategoryType.SOURCE_REFERENCE);
                                        break;
                                    default:
                                        referencesType.setReferenceCategory(_int.iho.s124.gml.cs0._0.ReferenceCategoryType.SOURCE_REFERENCE);
                                }

                                IMemberType imember = s124ObjectFactory.createIMemberType();
                                JAXBElement<ReferencesType> references = s124ObjectFactory.createReferences(referencesType);
                                imember.setInformationType(references);
                                gml.getImemberOrMember().add(imember);
                            } else
                                log.warn("References to main type {} ({}) are not supported. Seen on: {}", refType.getMainType(), refMrn, mrn);

                        }
                    } else {
                        log.error("Missing reference messageId on NW: {}", mrn);
                    }
                }
            });
        }
    }

    private BoundingShapeType createBoundingBox(FeatureCollectionVo[] geoJson) {
        BoundingShapeType boundingShapeType = null;

        double[] bbox = GeoJsonUtils.computeBBox(geoJson);
        if (bbox != null) {
            DirectPositionType lowerCorner = new DirectPositionType();
            lowerCorner.getValue().addAll(Lists.newArrayList(bbox[1], bbox[0]));

            DirectPositionType upperCorner = new DirectPositionType();
            upperCorner.getValue().addAll(Lists.newArrayList(bbox[3], bbox[2]));

            EnvelopeType envelopeType = new EnvelopeType();
            envelopeType.setSrsName("EPSG:4326");
            envelopeType.setLowerCorner(lowerCorner);
            envelopeType.setUpperCorner(upperCorner);

            boundingShapeType = new BoundingShapeType();
            boundingShapeType.setEnvelope(envelopeType);
        }

        return boundingShapeType;
    }

    private MessageSeriesIdentifierType createMessageSeries(Type type, int warningNumber, int year, String mrn, String lang) {
        if (type.getMainType() != NW)
            log.warn("References with main type {} are not support", type.getMainType());

        MessageSeriesIdentifierType messageSeriesIdentifierType = s124ObjectFactory.createMessageSeriesIdentifierType();
        messageSeriesIdentifierType.setWarningIdentifier(mrn);
        messageSeriesIdentifierType.setWarningNumber(warningNumber);
        messageSeriesIdentifierType.setYear(year);
        messageSeriesIdentifierType.setCountry("DK");
        messageSeriesIdentifierType.setProductionAgency("111");
        messageSeriesIdentifierType.setNameOfSeries("Danish Nav Warn");

        switch (type) {
            case LOCAL_WARNING:
                messageSeriesIdentifierType.setWarningType(WarningType.LOCAL_NAVIGATIONAL_WARNING);
                break;
            case COASTAL_WARNING:
                messageSeriesIdentifierType.setWarningType(WarningType.COASTAL_NAVIGATIONAL_WARNING);
                break;
            case SUBAREA_WARNING:
                messageSeriesIdentifierType.setWarningType(WarningType.SUB_AREA_NAVIGATIONAL_WARNING);
                break;
            case NAVAREA_WARNING:
                messageSeriesIdentifierType.setWarningType(WarningType.NAVAREA_NAVIGATIONAL_WARNING);
                break;
            default:
                log.warn("Messages of type {} not mapped.", type.name());
        }

        return messageSeriesIdentifierType;
    }

    private NavigationalWarningFeaturePartType createNavWarnPart(MessagePartVo partVo, String lang, String id, String mrn) {
        NavigationalWarningFeaturePartType navigationalWarningFeaturePartType = s124ObjectFactory.createNavigationalWarningFeaturePartType();
        navigationalWarningFeaturePartType.setId(String.format("%s.%d", id, partVo.getIndexNo() + 1));

        // ---

        /*
            TODO
            Former: "<id>urn:mrn:iho:nw:dk:nw-015-17.1</id> "
        */
        FeatureObjectIdentifier featureObjectIdentifier = s100ObjectFactory.createFeatureObjectIdentifier();
        featureObjectIdentifier.setFeatureIdentificationNumber(9999);
        featureObjectIdentifier.setFeatureIdentificationSubdivision(9999);
        featureObjectIdentifier.setAgency("99");
        navigationalWarningFeaturePartType.setFeatureObjectIdentifier(featureObjectIdentifier);

        // ---

        FeatureCollectionVo featureCollectionVo = new FeatureCollectionVo();
        featureCollectionVo.setFeatures(partVo.getGeometry().getFeatures());

        BoundingShapeType bbox = createBoundingBox(new FeatureCollectionVo[]{featureCollectionVo});

        BoundingShapeType boundingShapeType = gmlObjectFactory.createBoundingShapeType();
        boundingShapeType.setEnvelope(bbox.getEnvelope());
        navigationalWarningFeaturePartType.setBoundedBy(boundingShapeType);

        // ---

        if (partVo.getGeometry() != null && partVo.getGeometry().getFeatures() != null) {
            Stream.of(partVo.getGeometry().getFeatures()).forEach(feature -> {
                navigationalWarningFeaturePartType.getGeometry().addAll(createGeometry(id, feature.getGeometry()));
            });
        }

        // ---

        MessagePartDescVo partDesc = partVo.getDesc(lang);

        if (partDesc != null && StringUtils.isNotBlank(partDesc.getDetails())) {
            WarningInformationType warningInformationType = s124ObjectFactory.createWarningInformationType();
            warningInformationType.setLanguage(lang(partDesc.getLang()));
            warningInformationType.setText(asPlainText(partDesc.getDetails()));
            navigationalWarningFeaturePartType.getWarningInformation().add(warningInformationType);
        }

        // ---

        FixedDateRangeType fixedDateRangeType = s124ObjectFactory.createFixedDateRangeType();
        navigationalWarningFeaturePartType.setFixedDateRange(fixedDateRangeType);

        List<DateIntervalVo> eventDatesVo = partVo.getEventDates();
        if (eventDatesVo != null && eventDatesVo.size() > 0) {
            if (eventDatesVo.size() > 1)
                log.warn("There are " + eventDatesVo.size() + " event dates - but XML only supports one.");

            DateIntervalVo eventDateVo = eventDatesVo.get(0);
            boolean allDay = eventDateVo.getAllDay() == Boolean.TRUE;
            Date fromDate = eventDateVo.getFromDate();
            Date toDate = eventDateVo.getToDate();

            if (fromDate != null && !allDay) {
                try {
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTime(fromDate);
                    fixedDateRangeType.setTimeOfDayStart(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
                } catch (DatatypeConfigurationException e) {
                    log.error(e.getMessage(), e);
                }
            }

            if (toDate != null && !allDay) {
                try {
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTime(toDate);
                    fixedDateRangeType.setTimeOfDayEnd(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));
                } catch (DatatypeConfigurationException e) {
                    log.error(e.getMessage(), e);
                }
            }

            if (fromDate != null) {
                try {
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTime(fromDate);

                    S100TruncatedDate s100TruncatedDate = s124ObjectFactory.createS100TruncatedDate();
                    s100TruncatedDate.setDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));

                    fixedDateRangeType.setDateStart(s100TruncatedDate);
                } catch (DatatypeConfigurationException e) {
                    log.error(e.getMessage(), e);
                }
            }

            if (toDate != null) {
                try {
                    GregorianCalendar cal = new GregorianCalendar();
                    cal.setTime(toDate);

                    S100TruncatedDate s100TruncatedDate = s124ObjectFactory.createS100TruncatedDate();
                    s100TruncatedDate.setDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(cal));

                    fixedDateRangeType.setDateEnd(s100TruncatedDate);
                } catch (DatatypeConfigurationException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }

        // ---

        ReferenceType referenceType = gmlObjectFactory.createReferenceType();
        referenceType.setHref(String.format("#PR.%s", id));
        navigationalWarningFeaturePartType.setHeader(referenceType);

        // ---

        return navigationalWarningFeaturePartType;
    }

    private DirectPositionListType createCoordinates(double[][] coords) {
        DirectPositionListType directPositionListType = gmlObjectFactory.createDirectPositionListType();

        List<Double> coordColl = new ArrayList<>(coords.length * 2);

        for (int i = 0; i < coords.length; i++) {
            double[] coord = coords[i];
            for (int j = coord.length - 1; j >= 0; j--) {
                coordColl.add(coord[j]);
            }
        }

        directPositionListType.getValue().addAll(coordColl);
        return directPositionListType;
    }

    private PointPropertyType generatePoint(String id, double[] coords) {
        PointPropertyType pointPropertyType = null;

        if (coords != null && coords.length > 1) {
            Double[] boxedCoords = ArrayUtils.toObject(coords);

            DirectPositionType directPositionType = gmlObjectFactory.createDirectPositionType();
            directPositionType.getValue().addAll(reverse(asList(boxedCoords)));

            PointType pointType = s100ObjectFactory.createPointType();
            pointType.setId(nextGeomId(id));
            pointType.setSrsName("EPSG:4326");
            pointType.setPos(directPositionType);

            pointPropertyType = s100ObjectFactory.createPointPropertyType();
            pointPropertyType.setPoint(pointType);
        }

        return pointPropertyType;
    }

    private _int.iho.s100gml._1.CurvePropertyType createCurve(List<PointCurveSurface> geometry, String id, double[][] coords) {
        _int.iho.s100gml._1.CurvePropertyType curvePropertyType = null;

        if (coords != null && coords.length > 1) {
            DirectPositionListType directPositionListType = createCoordinates(coords);

            LineStringSegmentType lineStringSegmentType = gmlObjectFactory.createLineStringSegmentType();
            lineStringSegmentType.setPosList(directPositionListType);

            JAXBElement<LineStringSegmentType> lineStringSegment = gmlObjectFactory.createLineStringSegment(lineStringSegmentType);

            CurveSegmentArrayPropertyType curveSegmentArrayPropertyType = gmlObjectFactory.createCurveSegmentArrayPropertyType();
            curveSegmentArrayPropertyType.getAbstractCurveSegment().add(lineStringSegment);

            _int.iho.s100gml._1.CurveType curveType = s100ObjectFactory.createCurveType();
            curveType.setId(nextGeomId(id));
            curveType.setSrsName("EPSG:4326");
            curveType.setSegments(curveSegmentArrayPropertyType);

            curvePropertyType = s100ObjectFactory.createCurvePropertyType();
            curvePropertyType.setCurve(curveType);
        }

        PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
        p.setCurveProperty(curvePropertyType);
        geometry.add(p);

        return curvePropertyType;
    }

    private void addSurface(List<PointCurveSurface> geometry, String id, double[][][] coords) {
        for (int i = 0; i < coords.length; i++) {
            addSurface(geometry, id, coords[i], i == 0);
        }
    }

    private void addSurface(List<PointCurveSurface> geometry, String id, double[][] coords, boolean isFirst) {
        _int.iho.s100gml._1.SurfacePropertyType surfacePropertyType = null;

        if (coords != null && coords.length > 0) {
            surfacePropertyType = s100ObjectFactory.createSurfacePropertyType();
            _int.iho.s100gml._1.SurfaceType surfaceType = s100ObjectFactory.createSurfaceType();
            JAXBElement<_int.iho.s100gml._1.SurfaceType> surface = s100ObjectFactory.createSurface(surfaceType);
            surfacePropertyType.setAbstractSurface(surface);
            surfaceType.setId(nextGeomId(id));
            surfaceType.setSrsName("EPSG:4326");

            SurfacePatchArrayPropertyType surfacePatchArrayPropertyType = gmlObjectFactory.createSurfacePatchArrayPropertyType();
            surfaceType.setPatches(surfacePatchArrayPropertyType);

            PolygonPatchType polygonPatchType = gmlObjectFactory.createPolygonPatchType();
            surfacePatchArrayPropertyType.getAbstractSurfacePatch().add(gmlObjectFactory.createPolygonPatch(polygonPatchType));

            LinearRingType linearRingType = gmlObjectFactory.createLinearRingType();
            linearRingType.setPosList(createCoordinates(coords));

            AbstractRingPropertyType abstractRingPropertyType = gmlObjectFactory.createAbstractRingPropertyType();
            abstractRingPropertyType.setAbstractRing(gmlObjectFactory.createLinearRing(linearRingType));

            if (isFirst) {
                JAXBElement<AbstractRingPropertyType> element = gmlObjectFactory.createExterior(abstractRingPropertyType);
                polygonPatchType.setExterior(element.getValue());
            } else {
                JAXBElement<AbstractRingPropertyType> element = gmlObjectFactory.createInterior(abstractRingPropertyType);
                polygonPatchType.getInterior().add(element.getValue());
            }
        }

        PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
        p.setSurfaceProperty(surfacePropertyType);
        geometry.add(p);
    }

    private List<PointCurveSurface> createGeometry(String id, GeometryVo g) {
        List<PointCurveSurface> geometry = Lists.newArrayList();

        if (g instanceof PointVo) {
            PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
            p.setPointProperty(generatePoint(id, ((PointVo) g).getCoordinates()));
            geometry.add(p);
        } else if (g instanceof MultiPointVo) {
            double[][] coords = ((MultiPointVo) g).getCoordinates();
            for (int i = 0; i < coords.length; i++) {
                PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
                p.setPointProperty(generatePoint(id, coords[i]));
                geometry.add(p);
            }
        } else if (g instanceof LineStringVo) {
            double[][] coords = ((LineStringVo) g).getCoordinates();
            createCurve(geometry, id, coords);
        } else if (g instanceof MultiLineStringVo) {
            double[][][] coordss = ((MultiLineStringVo) g).getCoordinates();
            for (int i = 0; i < coordss.length; i++) {
                createCurve(geometry, id, coordss[i]);
            }
        } else if (g instanceof PolygonVo) {
            addSurface(geometry, id, ((PolygonVo) g).getCoordinates());
        } else if (g instanceof MultiPolygonVo) {
            double[][][][] coordss = ((MultiPolygonVo) g).getCoordinates();
            for (int i = 0; i < coordss.length; i++) {
                addSurface(geometry, id, coordss[i]);
            }
        } else if (g instanceof GeometryCollectionVo) {
            Stream.of(((GeometryCollectionVo) g).getGeometries()).forEach(g1 -> geometry.addAll(createGeometry(id, g1)));
        } else
            throw new RuntimeException("Unsupported: " + g.getClass().getName());

        return geometry;
    }

    private LocalityType createLocality(LocalityType localityType, AreaVo area, String lang) {
        AreaDescVo areaDesc = area.getDesc(lang);
        if (areaDesc != null && areaDesc.getName() != null) {
            LocationNameType locationNameType = s124ObjectFactory.createLocationNameType();
            locationNameType.setLanguage(lang(lang));
            locationNameType.setText(areaDesc.getName());
            localityType.getLocationName().add(locationNameType);
        }

        if (area.getParent() != null && localityType.getLocationName().size() == 0) {
            createLocality(localityType, area.getParent(), lang);
        }

        return localityType;
    }

    private GeneralAreaType createArea(final GeneralAreaType generalAreaType, final AreaVo area) {
        AreaDescVo enAreaDesc = area.getDesc("en");
        String areaName = enAreaDesc.getName();
        log.debug(areaName + " " + area.getParent());

        if (!isBlank(areaName)) {
            Function<String, LocationNameType> produceLocationNameType = text -> {
                LocationNameType lnt = s124ObjectFactory.createLocationNameType();
                generalAreaType.getLocationName().add(lnt);
                lnt.setLanguage(lang("en"));
                lnt.setText(text);
                return lnt;
            };

            LocationNameType locationNameType = null;

            switch (areaName) {
                case "The Baltic Sea":
                    locationNameType = produceLocationNameType.apply("Baltic sea");
                    break;
                case "Skagerrak":
                    locationNameType = produceLocationNameType.apply("Skagerrak");
                    break;
                case "Kattegat":
                    locationNameType = produceLocationNameType.apply("Kattegat");
                    break;
                case "The Sound":
                    locationNameType = produceLocationNameType.apply("The Sound");
                    break;
                case "The Great Belt":
                case "The Little Belt":
                    locationNameType = produceLocationNameType.apply("The Belts");
                    break;
            }

            if (area.getParent() != null && locationNameType == null) {
                return createArea(generalAreaType, area.getParent());
            }
        }

        return generalAreaType;
    }

    private String toMrn(MessageVo msg) {
        final String internalId = msg.getShortId() != null ? msg.getShortId() : msg.getId();
        final String mrn = "urn:mrn:iho:" + msg.getMainType().name().toLowerCase() + ":dk:" + internalId.toLowerCase();
        return mrn;
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

    private String nextGeomId(String id) {
        return String.format("G.%s.%d", id, nextGeomId++);
    }

    private String asPlainText(String string) {
        return isHtml(string) ? htmlToPlainText(string) : string;
    }

    private boolean isHtml(String string) {
        return string.contains("<") || string.contains("&");
    }

    private String htmlToPlainText(String html) {
        return Jsoup.parse(html).text();
    }

}
