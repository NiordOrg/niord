package org.niord.s124;

import _int.iho.s100gml._1.PointPropertyType;
import _int.iho.s100gml._1.PointType;
import _int.iho.s124.gml.cs0._0.*;
import com.google.common.collect.Lists;
import net.opengis.gml._3.*;
import net.opengis.gml._3.ReferenceType;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.niord.core.NiordApp;
import org.niord.core.geojson.GeoJsonUtils;
import org.niord.core.message.Message;
import org.niord.core.message.vo.SystemMessageVo;
import org.niord.model.geojson.*;
import org.niord.model.message.*;
import org.slf4j.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.xml.bind.*;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.StringWriter;
import java.lang.Boolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

@RequestScoped
public class S124ModelToGmlConverter {

    private NiordApp app;
    private Logger log;

    private net.opengis.gml._3.ObjectFactory gmlObjectFactory;
    private _int.iho.s100gml._1.ObjectFactory s100ObjectFactory;
    private _int.iho.s124.gml.cs0._0.ObjectFactory s124ObjectFactory;

    public S124ModelToGmlConverter() {
        // CDI
    }

    @Inject
    public S124ModelToGmlConverter(Logger log, NiordApp niordApp, net.opengis.gml._3.ObjectFactory gmlObjectFactory, _int.iho.s100gml._1.ObjectFactory s100ObjectFactory, _int.iho.s124.gml.cs0._0.ObjectFactory s124ObjectFactory) {
        this.log = log;
        this.app = niordApp;
        this.gmlObjectFactory = gmlObjectFactory;
        this.s100ObjectFactory = s100ObjectFactory;
        this.s124ObjectFactory = s124ObjectFactory;
    }

    public JAXBElement<DatasetType> toGml(Message message, String _lang) {
        requireNonNull(message);

        // Validate the message
        if (message.getMainType() == MainType.NM)
            throw new IllegalArgumentException("Sadly, S-124 does not currently support Notices to Mariners T&P :-(");
        if (message.getNumber() == null)
            throw new IllegalArgumentException("Sadly, S-124 does not currently support un-numbered navigational warnings :-(");

        // Ensure we use a valid lang
        final String lang = app.getLanguage(_lang);

        //
        DatasetType gml = new DatasetType();

        //
        SystemMessageVo msg = message.toVo(SystemMessageVo.class, Message.MESSAGE_DETAILS_FILTER);
        msg.sort(lang);

        // ---

        final String internalId = msg.getShortId() != null ? msg.getShortId() : msg.getId();
        final String id = "DK." + internalId;
        final String mrn = "urn:mrn:iho:" + msg.getMainType().name().toLowerCase() + ":dk:" + internalId.toLowerCase();

        // ---

        double[] bbox = GeoJsonUtils.computeBBox(message.toGeoJson());
        if (bbox != null) {
            DirectPositionType lowerCorner = new DirectPositionType();
            lowerCorner.getValue().addAll(Lists.newArrayList(bbox[1], bbox[0]));

            DirectPositionType upperCorner = new DirectPositionType();
            upperCorner.getValue().addAll(Lists.newArrayList(bbox[3], bbox[2]));

            EnvelopeType envelopeType = new EnvelopeType();
            envelopeType.setSrsName("EPSG:4326");
            envelopeType.setLowerCorner(lowerCorner);
            envelopeType.setUpperCorner(upperCorner);

            BoundingShapeType boundingShapeType = new BoundingShapeType();
            boundingShapeType.setEnvelope(envelopeType);
            gml.setBoundedBy(boundingShapeType);
        }

        // ---

        generatePreamble(gml, lang, mrn, id, msg);

        if (msg.getParts() != null) {
            int partNo = msg.getParts().size();
            msg.getParts().forEach(part -> {
                if (part.getGeometry() != null && part.getGeometry().getFeatures() != null && part.getGeometry().getFeatures().length > 0) {
                    NavigationalWarningFeaturePartType navigationalWarningFeaturePartType = generateNavWarnPart(part, part.getIndexNo(), mrn, id, lang);
                    JAXBElement<NavigationalWarningFeaturePartType> navigationalWarningFeaturePart = s124ObjectFactory.createNavigationalWarningFeaturePart(navigationalWarningFeaturePartType);
                    MemberType memberType = s124ObjectFactory.createMemberType();
                    memberType.setAbstractFeature(navigationalWarningFeaturePart);
                    gml.getImemberOrMember().add(memberType);
                } else {
                    log.error("S124_InformationNoticePart not supported.");
                }
            });
        }

        /*
    <#if references?has_content>
        <#list references as ref>
            <imember>
                <@generateReference ref=ref index=ref?index + partNo></@generateReference>
            </imember>
        </#list>
    </#if>

        */

        // ---

        JAXBElement<DatasetType> dataSet = s124ObjectFactory.createDataSet(gml);
        return dataSet;
    }

    private NavigationalWarningFeaturePartType generateNavWarnPart(MessagePartVo partVo, int index, String id, String mrn, String lang) {
        NavigationalWarningFeaturePartType navigationalWarningFeaturePartType = s124ObjectFactory.createNavigationalWarningFeaturePartType();
        navigationalWarningFeaturePartType.setId(id + "." + partVo.getIndexNo() + 1);

        MessagePartDescVo partDesc = partVo.getDesc(lang);

        navigationalWarningFeaturePartType.setId(mrn + "." + (index + 1));

        if (partVo.getGeometry() != null && partVo.getGeometry().getFeatures() != null) {
            Stream.of(partVo.getGeometry().getFeatures()).forEach(feature -> {
                navigationalWarningFeaturePartType.getGeometry().add(generateGeometry(id, feature.getGeometry()).get(0));
            });
        }

/*


    <#if partVo.geometry?? && partVo.geometry.features?has_content>
        <#list partVo.geometry.features as feature>
            <@generateGeometry g=feature.geometry></@generateGeometry>
        </#list>
    </#if>
    */
        if (partDesc != null && StringUtils.isNotBlank(partDesc.getDetails())) {
            WarningInformationType warningInformationType = s124ObjectFactory.createWarningInformationType();
            warningInformationType.setLanguage(partDesc.getLang());
            warningInformationType.setText(partDesc.getDetails());
            navigationalWarningFeaturePartType.getWarningInformation().add(warningInformationType);
        }

/*

    <#if partVo.eventDates?? && partVo.eventDates?has_content>
        <#list partVo.eventDates as date>
            <#assign allDay=date.allDay?? && date.allDay == true />
            <fixedDateRange>
                <#if date.fromDate?? && !allDay>
                    <timeOfDayStart>${date.fromDate?string["HH:mm:ss"]}Z</timeOfDayStart>
                </#if>
                <#if date.toDate?? && !allDay>
                    <timeOfDayEnd>${date.toDate?string["HH:mm:ss"]}Z</timeOfDayEnd>
                </#if>
                <#if date.fromDate??>
                    <dateStart>
                        <date>${date.fromDate?string["yyyy-MM-dd"]}</date>
                    </dateStart>
                </#if>
                <#if date.toDate??>
                    <dateEnd>
                        <date>${date.toDate?string["yyyy-MM-dd"]}</date>
                    </dateEnd>
                </#if>
            </fixedDateRange>
        </#list>
    </#if>
    <header xlink:href = "#PR.${id}" ></header >
</#macro >
*/

        return navigationalWarningFeaturePartType;
    }

    private DirectPositionListType generateCoordinates(double[][] coords) {
        DirectPositionListType directPositionListType = gmlObjectFactory.createDirectPositionListType();

        List<Double> coordColl = new ArrayList<>(coords.length * 2);

        for (int i=0; i<coords.length; i++) {
            double[] coord = coords[i];
            for (int j=coord.length-1; j>=0; j--) {
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
            directPositionType.getValue().addAll(Arrays.asList(boxedCoords));

            PointType pointType = s100ObjectFactory.createPointType();
            pointType.setId(nextGeomId(id));
            pointType.setSrsName("EPSG:4326");
            pointType.setPos(directPositionType);

            pointPropertyType = s100ObjectFactory.createPointPropertyType();
            pointPropertyType.setPoint(pointType);
        }

        return pointPropertyType;
    }

    private _int.iho.s100gml._1.CurvePropertyType generateCurve(String id, double[][] coords) {
        _int.iho.s100gml._1.CurvePropertyType curvePropertyType = null;

        if (coords != null && coords.length > 1) {
            DirectPositionListType directPositionListType = generateCoordinates(coords);

            LineStringSegmentType lineStringSegmentType = gmlObjectFactory.createLineStringSegmentType();
            lineStringSegmentType.setPosList(directPositionListType);

            JAXBElement<LineStringSegmentType> lineStringSegment = gmlObjectFactory.createLineStringSegment(lineStringSegmentType);

            CurveSegmentArrayPropertyType curveSegmentArrayPropertyType = gmlObjectFactory.createCurveSegmentArrayPropertyType();
            curveSegmentArrayPropertyType.getAbstractCurveSegment().add(lineStringSegment);

            curvePropertyType = s100ObjectFactory.createCurvePropertyType();
            CurveType curveType = s100ObjectFactory.createCurveType();
            curveType.setId(nextGeomId(id));
            curveType.setSrsName("EPSG:4326");
            curveType.setSegments(curveSegmentArrayPropertyType);

        }

        return curvePropertyType;
    }

    private _int.iho.s100gml._1.SurfacePropertyType generateSurface(String id, double[][] coords, boolean isFirst) {
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
            linearRingType.setPosList(generateCoordinates(coords));

            AbstractRingPropertyType abstractRingPropertyType = gmlObjectFactory.createAbstractRingPropertyType();
            abstractRingPropertyType.setAbstractRing(gmlObjectFactory.createAbstractRing(linearRingType));

            if (isFirst) {
                JAXBElement<AbstractRingPropertyType> element = gmlObjectFactory.createExterior(abstractRingPropertyType);
                polygonPatchType.setExterior(element.getValue());
            } else {
                JAXBElement<AbstractRingPropertyType> element = gmlObjectFactory.createInterior(abstractRingPropertyType);
                polygonPatchType.getInterior().add(element.getValue());
            }
        }

        return surfacePropertyType;
    }

    private List<PointCurveSurface> generateGeometry(String id, GeometryVo g) {
        List<PointCurveSurface> geometry = Lists.newArrayList();

        if (g instanceof PointVo) {
            PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
            p.setPointProperty(generatePoint(id, ((PointVo) g).getCoordinates()));
            geometry.add(p);
        } else if (g instanceof MultiPointVo) {
            double[][] coords = ((MultiPointVo) g).getCoordinates();
            for (double[] coord : coords) {
                PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
                p.setPointProperty(generatePoint(id, ((PointVo) g).getCoordinates()));
                geometry.add(p);
            }
        } else if (g instanceof LineStringVo) {
            double[][] coords = ((LineStringVo) g).getCoordinates();
            _int.iho.s100gml._1.CurvePropertyType curvePropertyType = generateCurve(id, coords);

            PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
            p.setCurveProperty(curvePropertyType);
            geometry.add(p);
        } else if (g instanceof MultiLineStringVo) {
            double[][][] coords = ((MultiLineStringVo) g).getCoordinates();
            for (int i = 0; i < coords.length; i++) {
                _int.iho.s100gml._1.CurvePropertyType curvePropertyType = generateCurve(id, coords[i]);
                PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
                p.setCurveProperty(curvePropertyType);
                geometry.add(p);
            }
        } else if (g instanceof PolygonVo) {
            double[][][] coords = ((PolygonVo) g).getCoordinates();
            for (int i = 0; i < coords.length; i++) {
                _int.iho.s100gml._1.SurfacePropertyType surfacePropertyType = generateSurface(id, coords[i], i == 0);
                PointCurveSurface p = s124ObjectFactory.createPointCurveSurface();
                p.setSurfaceProperty(surfacePropertyType);
                geometry.add(p);
            }
        } else if (g instanceof MultiPolygonVo) {
            throw new RuntimeException("NYI: " + g.getClass().getName());
            /*
            <#list g.coordinates as coords>
                <@generateSurface coords=coords></@generateSurface>
            </#list>
            */
        } else if (g instanceof GeometryCollectionVo) {
            throw new RuntimeException("NYI: " + g.getClass().getName());
            /*
             <#list g.geometries as geom>
                <@generateGeometry g=geom></@generateGeometry>
            </#list>
             */
        } else
            throw new RuntimeException("Unsupported: " + g.getClass().getName());

        return geometry;
    }

    private void generatePreamble(DatasetType datasetType, String lang, String mrn, String id, SystemMessageVo msg) {
        NWPreambleType nwPreambleType = s124ObjectFactory.createNWPreambleType();

        datasetType.setId(id);

        // ---

        MessageSeriesIdentifierType messageSeriesIdentifierType = s124ObjectFactory.createMessageSeriesIdentifierType();
        messageSeriesIdentifierType.setWarningIdentifier(mrn);
        messageSeriesIdentifierType.setWarningNumber(msg.getNumber() != null ? msg.getNumber() : -1);
        messageSeriesIdentifierType.setYear(msg.getYear() != null ? msg.getYear() % 100 : 0);
        messageSeriesIdentifierType.setCountry("DK");

        switch (msg.getType()) {
            case LOCAL_WARNING:
                messageSeriesIdentifierType.setNameOfSeries("Danish Nav Warn");
                messageSeriesIdentifierType.setWarningType(WarningType.LOCAL_NAVIGATIONAL_WARNING);
                break;
            case COASTAL_WARNING:
                messageSeriesIdentifierType.setNameOfSeries("Danish Nav Warn");
                messageSeriesIdentifierType.setWarningType(WarningType.COASTAL_NAVIGATIONAL_WARNING);
                break;
            case SUBAREA_WARNING:
                messageSeriesIdentifierType.setNameOfSeries("Danish Nav Warn");
                messageSeriesIdentifierType.setWarningType(WarningType.SUB_AREA_NAVIGATIONAL_WARNING);
                break;
            case NAVAREA_WARNING:
                messageSeriesIdentifierType.setNameOfSeries("Danish Nav Warn");
                messageSeriesIdentifierType.setWarningType(WarningType.NAVAREA_NAVIGATIONAL_WARNING);
                break;
            default:
                log.warn("Messages of type {} not mapped.", msg.getType().name());
        }

        // ---

        if (lang.equalsIgnoreCase("da"))
            messageSeriesIdentifierType.setProductionAgency("SØFARTSSTYRELSEN");
        else
            messageSeriesIdentifierType.setProductionAgency("DANISH MARITIME AUTHORITY");

        nwPreambleType.setMessageSeriesIdentifier(messageSeriesIdentifierType);
        nwPreambleType.setId("PR." + id);

        // ---

        MessageDescVo msgDesc = msg.getDesc(lang);

        if (msgDesc != null && !StringUtils.isBlank(msgDesc.getTitle())) {
            TitleType titleType = s124ObjectFactory.createTitleType();
            titleType.setLanguage(msgDesc.getLang());
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
            GeneralAreaType generalAreaType = generateArea(s124ObjectFactory.createGeneralAreaType(), area, area, "en");
            nwPreambleType.getGeneralArea().add(generalAreaType);

            LocalityType localityType = generateLocality(s124ObjectFactory.createLocalityType(), area, area, "en");
            nwPreambleType.getLocality().add(localityType);
        });

        // ---

        msg.getParts().forEach(part -> {
            ReferenceType referenceType = gmlObjectFactory.createReferenceType();
            referenceType.setHref(String.format("#%s.%d", id, part.getIndexNo() + 1));
            nwPreambleType.getTheWarningPart().add(referenceType);
        });

        /*
            TODO referenced messages

        <#if references?has_content>
            <#list references as ref>
                <theWarningPart xlink:href="#${id}.${ref?index + partNo + 1}"></theWarningPart>
            </#list>
        </#if>
         */

        // ---

        JAXBElement<NWPreambleType> nwPreambleTypeElement = s124ObjectFactory.createNWPreamble(nwPreambleType);

        IMemberType imember = s124ObjectFactory.createIMemberType();
        imember.setInformationType(nwPreambleTypeElement);

        datasetType.getImemberOrMember().add(imember);
    }

    private LocalityType generateLocality(LocalityType localityType, AreaVo area, AreaVo rootArea, String lang) {
        AreaDescVo areaDesc = area.getDesc(lang);
        if (areaDesc != null && areaDesc.getName() != null) {
            LocationNameType locationNameType = s124ObjectFactory.createLocationNameType();
            locationNameType.setLanguage(lang);
            locationNameType.setText(areaDesc.getName());
            localityType.getLocationName().add(locationNameType);
        }

        if (area.getParent() != null) {
            generateLocality(localityType, area.getParent(), rootArea, lang);
        }

        return localityType;
    }

    private GeneralAreaType generateArea(GeneralAreaType generalAreaType, AreaVo msgArea, AreaVo area, String lang) {
        AreaDescVo enAreaDesc = area.getDesc(lang);

        log.info(enAreaDesc.getName() + " " + area.getParent());

        if (!isBlank(enAreaDesc.getName())) {
            switch (enAreaDesc.getName()) {
                case "The Baltic Sea":
                    generalAreaType.setOcalityIdentifier("Baltic sea");
                    break;
                case "Skagerrak":
                    generalAreaType.setOcalityIdentifier("Skagerrak");
                    break;
                case "Kattegat":
                    generalAreaType.setOcalityIdentifier("Kattegat");
                    break;
                case "The Sound":
                    generalAreaType.setOcalityIdentifier("The Sound");
                    break;
                case "The Great Belt":
                case "The Little Belt":
                    generalAreaType.setOcalityIdentifier("The Belts");
                    break;
            }

            if (area.getParent() != null) {
                generalAreaType = generateArea(generalAreaType, msgArea, area.getParent(), lang);
            }
        }

        return generalAreaType;
    }

    private int nextGeomId = 1;

    private String nextGeomId(String id) {
        return String.format("G.%s.%d", id, nextGeomId++);
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
