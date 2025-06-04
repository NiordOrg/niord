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

import org.junit.Before;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.niord.core.area.Area;
import org.niord.core.area.AreaDesc;
import org.niord.core.category.Category;
import org.niord.core.chart.Chart;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.*;
import org.niord.model.message.MainType;
import org.niord.model.message.MessagePartType;
import org.niord.model.message.ReferenceType;
import org.niord.model.message.Status;
import org.niord.model.message.Type;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Base class for S-124 mapper tests providing common test data and utilities
 */
public abstract class S124TestBase {

    protected static final String TEST_COUNTRY = "DK";
    protected static final String TEST_PRODUCTION_AGENCY = "Danish Maritime Authority";
    protected static final String TEST_LANGUAGE = "en";

    protected GeometryFactory geometryFactory;
    protected S124DatasetInfo datasetInfo;

    @Before
    public void setupBase() {
        geometryFactory = new GeometryFactory();
        datasetInfo = createTestDatasetInfo();
    }

    protected S124DatasetInfo createTestDatasetInfo() {
        // Use constructor that matches the actual API
        S124DatasetInfo info = new S124DatasetInfo("urn:mrn:iho:dataset:dk:test:001", "DK-DMA-S124", new ArrayList<>());
        return info;
    }

    protected Message createBasicMessage() {
        Message msg = new Message();
        msg.setId(123);
        msg.setShortId("DK-001-24");
        msg.setMainType(MainType.NW);
        msg.setType(Type.COASTAL_WARNING);
        msg.setStatus(Status.PUBLISHED);
        msg.setNumber(42);

        // Set dates
        msg.setPublishDateFrom(Date.from(LocalDateTime.of(2024, 1, 15, 10, 0)
                .atZone(ZoneId.systemDefault()).toInstant()));
        msg.setPublishDateTo(Date.from(LocalDateTime.of(2024, 12, 31, 23, 59)
                .atZone(ZoneId.systemDefault()).toInstant()));

        // Set message series
        MessageSeries series = new MessageSeries();
        series.setSeriesId("DK-NW");
        msg.setMessageSeries(series);

        // Add basic description
        MessageDesc desc = new MessageDesc();
        desc.setLang("en");
        desc.setTitle("Navigation warning test");
        desc.setVicinity("Great Belt");
        msg.getDescs().add(desc);

        return msg;
    }

    protected MessagePart createBasicMessagePart(int index) {
        MessagePart part = new MessagePart();
        part.setIndexNo(index);
        part.setType(MessagePartType.DETAILS);

        // Add description
        MessagePartDesc desc = new MessagePartDesc();
        desc.setLang("en");
        desc.setSubject("Test warning part " + index);
        desc.setDetails("Details for test warning part " + index);
        part.getDescs().add(desc);

        // Add event dates
        DateInterval interval = new DateInterval();
        interval.setFromDate(Date.from(LocalDateTime.of(2024, 2, 1, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant()));
        interval.setToDate(Date.from(LocalDateTime.of(2024, 3, 1, 0, 0)
                .atZone(ZoneId.systemDefault()).toInstant()));
        part.getEventDates().add(interval);

        return part;
    }

    protected Area createTestArea(String id, String name) {
        Area area = new Area();
        area.setId(Math.abs(id.hashCode() % 999999) + 1);

        AreaDesc desc = new AreaDesc();
        desc.setLang("en");
        desc.setName(name);
        area.getDescs().add(desc);

        // Add geometry - a simple polygon around Denmark
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(8.0, 54.5),
            new Coordinate(8.0, 57.5),
            new Coordinate(13.0, 57.5),
            new Coordinate(13.0, 54.5),
            new Coordinate(8.0, 54.5)
        };
        area.setGeometry(geometryFactory.createPolygon(coords));

        return area;
    }

    protected Chart createTestChart(String number, Date published) {
        Chart chart = new Chart();
        chart.setChartNumber(number);
        // Chart.setPublished method doesn't exist, skip setting published date
        chart.setInternationalNumber(Integer.parseInt(number));
        return chart;
    }

    protected Category createTestCategory(String name) {
        Category category = new Category();
        category.setId(Math.abs(name.hashCode() % 999999) + 1);
        // Set the legacy ID to the original string for mapping purposes
        category.setLegacyId(name);

        // Add category description in English
        var desc = new org.niord.core.category.CategoryDesc();
        desc.setLang("en");
        desc.setName(name);
        category.getDescs().add(desc);

        return category;
    }

    protected FeatureCollection createPointGeometry(double lon, double lat) {
        FeatureCollection fc = new FeatureCollection();
        Feature feature = new Feature();

        Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
        feature.setGeometry(point);
        feature.getProperties().put("name", "Test Point");

        fc.getFeatures().add(feature);
        return fc;
    }

    protected FeatureCollection createPolygonGeometry() {
        FeatureCollection fc = new FeatureCollection();
        Feature feature = new Feature();

        Coordinate[] coords = new Coordinate[] {
            new Coordinate(10.5, 55.5),
            new Coordinate(10.5, 56.0),
            new Coordinate(11.0, 56.0),
            new Coordinate(11.0, 55.5),
            new Coordinate(10.5, 55.5)
        };

        Polygon polygon = geometryFactory.createPolygon(coords);
        feature.setGeometry(polygon);
        feature.getProperties().put("name", "Test Area");

        fc.getFeatures().add(feature);
        return fc;
    }

    protected Reference createReference(Message referencedMessage, ReferenceType type) {
        Reference ref = new Reference();
        ref.setMessage(referencedMessage);
        ref.setType(type);
        ref.setMessageId(referencedMessage.getShortId());
        return ref;
    }

    protected void addMultiLanguageDescriptions(Message message) {
        // Danish description
        MessageDesc dkDesc = new MessageDesc();
        dkDesc.setLang("da");
        dkDesc.setTitle("Navigationsadvarsel test");
        dkDesc.setVicinity("Storebælt");
        message.getDescs().add(dkDesc);

        // German description
        MessageDesc deDesc = new MessageDesc();
        deDesc.setLang("de");
        deDesc.setTitle("Navigationswarnung Test");
        deDesc.setVicinity("Großer Belt");
        message.getDescs().add(deDesc);
    }

    protected void assertValidMrn(String mrn) {
        org.junit.Assert.assertNotNull("MRN should not be null", mrn);
        org.junit.Assert.assertTrue("MRN should start with urn:mrn:", mrn.startsWith("urn:mrn:"));
    }

    protected void assertValidCoordinateOrder(Double[] coords, String description) {
        org.junit.Assert.assertNotNull(description + " coordinates should not be null", coords);
        org.junit.Assert.assertEquals(description + " should have exactly 2 coordinates", 2, coords.length);
        // For lat,lon order: latitude should be between -90 and 90
        org.junit.Assert.assertTrue(description + " first coordinate should be latitude (-90 to 90), but was " + coords[0],
                coords[0] >= -90 && coords[0] <= 90);
        // Longitude should be between -180 and 180
        org.junit.Assert.assertTrue(description + " second coordinate should be longitude (-180 to 180), but was " + coords[1],
                coords[1] >= -180 && coords[1] <= 180);
    }
}