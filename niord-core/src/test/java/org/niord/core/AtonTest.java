/*
 * Copyright 2016 Danish Maritime Authority.
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

package org.niord.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.niord.core.aton.AtonFilter;
import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.aton.vo.AtonOsmVo;
import org.niord.core.aton.vo.AtonTagVo;
import org.niord.core.aton.vo.Iso8601DateXmlAdapter;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for the OSM AtoN model
 */
public class AtonTest {

    @Test
    public void testAtonSeamark() throws Exception {
        AtonNodeVo aton = createAtonNode();
        Assert.assertTrue(aton.validSeamark());
    }


    @Test
    public void testAtonOsm() throws Exception {

        AtonOsmVo osm = new AtonOsmVo();
        osm.setVersion(0.6f);
        List<AtonNodeVo> atons = new ArrayList<>();
        atons.add(createAtonNode());
        atons.add(createAtonNode());
        atons.add(createAtonNode());
        osm.setNodes(atons.toArray(new AtonNodeVo[atons.size()]));
        osm.computeBounds();

        // Validate the OSM against the OSMSchema.xsd, originally from:
        // https://github.com/oschrenk/osm/blob/master/osm-io/src/main/resources/OSMSchema.xsd
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(getClass().getResource("/OSMSchema.xsd"));

        JAXBContext jaxbContext = JAXBContext.newInstance(AtonOsmVo.class);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setSchema(schema);
        marshaller.marshal(osm, System.out);
    }


    @Test
    public void testAtonXml() throws Exception {
        AtonNodeVo aton = createAtonNode();

        JAXBContext jaxbContext = JAXBContext.newInstance(AtonNodeVo.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        AtonNodeVo aton2 = (AtonNodeVo) unmarshaller.unmarshal(getClass().getResourceAsStream("/seamark-node.xml"));

        Assert.assertNotNull(aton2.getTags());
        Assert.assertEquals(aton2.getTags().length, 10);
        Assert.assertEquals(aton2.getId(), 672436827L);

        Assert.assertEquals(aton, aton2);
    }


    @Test
    public void testAtonJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        AtonNodeVo aton = createAtonNode();
        String json = mapper.writeValueAsString(aton);
        AtonNodeVo aton2 = mapper.readValue(json, AtonNodeVo.class);

        Assert.assertEquals(aton, aton2);
    }


    @Test
    public void testAtonMatching() throws Exception {

        AtonNodeVo aton = createAtonNode();

        Assert.assertTrue(aton.k("seamark:type"));
        Assert.assertTrue(aton.kv("seamark:type", "ged|buoy.*"));
    }


    @Test
    public void testAtonFilter() throws Exception {

        AtonNodeVo aton = createAtonNode();

        AtonFilter filter = AtonFilter.getInstance("aton.kv('seamark:type', 'buoy.*')");

        Assert.assertTrue(filter.matches(aton));
    }


    /** Constructs an AtoN programmatically */
    private AtonNodeVo createAtonNode() throws Exception {
        AtonNodeVo aton = new AtonNodeVo();
        aton.setId(672436827);
        aton.setLat(50.8070813);
        aton.setLon(-1.2841124);
        aton.setUser("malcolmh");
        aton.setUid(128186);
        aton.setVisible(true);
        aton.setVersion(11);
        aton.setChangeset(9107813);
        aton.setTimestamp(new Iso8601DateXmlAdapter().unmarshal("2011-08-23T21:22:36Z"));
        List<AtonTagVo> tags = new ArrayList<>();
        tags.add(new AtonTagVo("seamark:buoy_cardinal:category", "north"));
        tags.add(new AtonTagVo("seamark:buoy_cardinal:colour", "black;yellow"));
        tags.add(new AtonTagVo("seamark:buoy_cardinal:colour_pattern", "horizontal"));
        tags.add(new AtonTagVo("seamark:buoy_cardinal:shape", "pillar"));
        tags.add(new AtonTagVo("seamark:light:character", "VQ"));
        tags.add(new AtonTagVo("seamark:light:colour", "white"));
        tags.add(new AtonTagVo("seamark:name", "Calshot"));
        tags.add(new AtonTagVo("seamark:topmark:colour", "black"));
        tags.add(new AtonTagVo("seamark:topmark:shape", "2 cones up"));
        tags.add(new AtonTagVo("seamark:type", "buoy_cardinal"));
        aton.setTags(tags.toArray(new AtonTagVo[tags.size()]));
        return aton;
    }
}
