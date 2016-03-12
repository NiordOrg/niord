/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.core.aton;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates default OSM node-tag sets based on INT-1-Presets.xml based on:
 * https://raw.githubusercontent.com/OpenSeaMap/josm/master/INT-1-preset.xml
 *
 * <p>
 * NB: Remove the "xmlns" attribute from the root &lt;presets&gt; element before checking the file into this project.
 * <p>
 * The XML file is transformed to a more manageable format for relevant node types, via the aton-osm-defaults.xslt
 * transformation. Snippet from result XML file:
 *
 * <pre>
 * &lt;osm-defaults&gt;
 *   &lt;tag-values id="buoyshapes"&gt;
 *     &lt;tag-value v="conical"/&gt;
 *     &lt;tag-value v="can"/&gt;
 *     &lt;tag-value v="spherical"/&gt;
 *     &lt;tag-value v="super-buoy"/&gt;
 *     &lt;tag-value v="pillar"/&gt;
 *     &lt;tag-value v="spar"/&gt;
 *     &lt;tag-value v="barrel"/&gt;
 *     &lt;tag-value v="ice-buoy"/&gt;
 *   &lt;/tag-values&gt;
 *   ...
 *   &lt;node-type name="Isolated Danger Topmark"&gt;
 *     &lt;tag k="seamark:topmark:shape" v="2 spheres"/&gt;
 *     &lt;tag k="seamark:topmark:colour" v="black"/&gt;
 *     &lt;tag k="seamark:topmark:colour_patern" v=""/&gt;
 *   &lt;/node-type&gt;
 *   &lt;node-type name="Isolated Danger Buoy"&gt;
 *     &lt;tag k="seamark:type" v="buoy_isolated_danger"/&gt;
 *     &lt;tag k="seamark:buoy_isolated_danger:shape"&gt;
 *       &lt;tag-values ref="buoyshapes"/&gt;
 *     &lt;/tag&gt;
 *     &lt;tag k="seamark:buoy_isolated_danger:colour"&gt;
 *       &lt;tag-values ref="cardinalcolours"/&gt;
 *     &lt;/tag&gt;
 *     &lt;tag k="seamark:buoy_isolated_danger:colour_pattern"&gt;
 *       &lt;tag-values ref="patterns"/&gt;
 *     &lt;/tag&gt;
 *     &lt;tag k="seamark:name"/&gt;
 *   &lt;/node-type&gt;
 *   ...
 * </pre>
 *
 * <p>
 * Testing: From the command lines, run:
 * <pre>
 *     xsltproc --stringparam ialaSkipSystem "IALA-B"  aton-osm-defaults.xslt INT-1-preset.xml &gt; result.xml
 * </pre>
 *
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class AtonDefaultsService {

    @Inject
    private Logger log;

    @Resource
    TimerService timerService;

    // TODO: Inject from setting
    IalaBuoyageSystem ialaSystem = IalaBuoyageSystem.IALA_A;

    private OsmDefaults osmDefaults;

    // Look-up table for tag-values
    private Map<String, ODTagValues> osmTagValues = new HashMap<>();
    private Map<String, ODNodeType> osmNodeTypes = new HashMap<>();


    /** Called upon application startup */
    @PostConstruct
    public void init() {
        // In order not to stall webapp deployment, wait 3 seconds before initializing the defaults
        timerService.createSingleActionTimer(3000, new TimerConfig());
    }

    /**
     * Generates the AtoN defaults from the INT-1-preset.xml file
     */
    @Timeout
    private void generateDefaults() {
        try {
            long t0 = System.currentTimeMillis();
            Source xsltSource = new StreamSource(getClass().getResourceAsStream("/aton/aton-osm-defaults.xslt"));
            Source xmlSource = new StreamSource(getClass().getResourceAsStream("/aton/INT-1-preset.xml"));

            // Capture the generated xml as a string
            StringWriter xml = new StringWriter();
            Result result = new StreamResult(xml);

            // Execute the xslt
            TransformerFactory transFact = TransformerFactory.newInstance();
            Transformer trans = transFact.newTransformer(xsltSource);
            trans.setParameter("ialaSkipSystem", ialaSystem.other().toString());
            trans.transform(xmlSource, result);

            // Read in the result as OsmDefaults data
            JAXBContext jaxbContext = JAXBContext.newInstance(OsmDefaults.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            osmDefaults = (OsmDefaults) unmarshaller.unmarshal(new StringReader(xml.toString()));

            // Build look-up tables for fast access
            osmDefaults.getTagValues().stream()
                    .forEach(tv -> osmTagValues.put(tv.getId(), tv));
            osmDefaults.getNodeTypes().stream()
                    .forEach(nt -> osmNodeTypes.put(nt.getName(), nt));

            log.info("********* Created AtoN defaults in " + (System.currentTimeMillis() - t0) +  " ms");
        } catch (Exception e) {
            log.error("Failed creating AtoN defaults in " + e, e);
        }
    }

    /**
     * Returns the name of all node types where the name matches the parameter
     *
     * @param name the substring match
     * @return the name of all node types where the name matches the parameter
     */
    public List<String> getNodeTypeNames(String name) {
        return osmDefaults.getNodeTypes().stream()
                .map(ODNodeType::getName)
                .filter(n -> name == null || StringUtils.containsIgnoreCase(n, name))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }


    /**
     * Merges the given AtoN with the tags of the node type with the given name
     *
     * @param aton the AtoN to update
     * @param nodeTypeName type names
     */
    public void mergeAtonWithNodeTypes(AtonNode aton, String nodeTypeName) {

        // Sanity checks
        if (aton == null || StringUtils.isBlank(nodeTypeName) || !osmNodeTypes.containsKey(nodeTypeName)) {
            return;
        }

        osmNodeTypes.get(nodeTypeName).getTags().forEach(tag -> {
            if (aton.getTagValue(tag.getK()) == null) {
                String v = StringUtils.defaultString(tag.getV());
                aton.getTags().add(new AtonTag(tag.getK(), v));
            }
        });
    }


    public List<String> getKeysForAton(AtonNode aton) {
        return getNodeTypeForAton(aton).stream()
                .flatMap(nt -> nt.getTags().stream())
                .map(ODTag::getK)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getValuesForAtonAndKey(AtonNode aton, String key) {
        return getNodeTypeForAton(aton).stream()
                .flatMap(nt -> nt.getTags().stream())
                .filter(t -> key.equals(t.getK()))
                .filter(t -> t.getV() != null) // TODO refs
                .map(ODTag::getV)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private List<ODNodeType> getNodeTypeForAton(AtonNode aton) {
        String type = aton.getTagValue("seamark:type");
        if (StringUtils.isBlank(type)) {
            return Collections.emptyList();
        }
        return osmDefaults.getNodeTypes().stream()
                .filter(nt -> hasKeyValue(nt, "seamark:type", type))
                .collect(Collectors.toList());
    }

    private boolean hasKeyValue(ODNodeType nodeType, String k, String v) {
        for (ODTag tag : nodeType.getTags()) {
            if (v.equals(tag.getV())) {
                return true;
            }
        }
        return false;
    }


    /*************************/
    /** Helper classes      **/
    /*************************/

    /**
     * Root element in the OSM Defaults data generated by the aton-osm-defaults.xslt transformation
     */
    @XmlRootElement(name = "osm-defaults")
    public static class OsmDefaults {

        List<ODTagValues> tagValues = new ArrayList<>();
        List<ODNodeType> nodeTypes;

        @XmlElement(name = "tag-values")
        public List<ODTagValues> getTagValues() {
            return tagValues;
        }

        public void setTagValues(List<ODTagValues> tagValues) {
            this.tagValues = tagValues;
        }

        @XmlElement(name = "node-type")
        public List<ODNodeType> getNodeTypes() {
            return nodeTypes;
        }

        public void setNodeTypes(List<ODNodeType> nodeTypes) {
            this.nodeTypes = nodeTypes;
        }
    }

    /**
     * Defines a node type
     */
    public static class ODNodeType {
        String name;
        List<ODTag> tags = new ArrayList<>();

        public boolean hasTagValue(String k, String v) {
            return tags.stream().anyMatch(t -> t.getK().matches(k) && v.equals(t.getV()));
        }

        @XmlAttribute
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XmlElement(name = "tag")
        public List<ODTag> getTags() {
            return tags;
        }

        public void setTags(List<ODTag> tags) {
            this.tags = tags;
        }
    }

    /**
     * Defines a node tag
     */
    public static class ODTag {
        String k;
        String v;
        List<ODTagValues> tagValues;

        @XmlAttribute
        public String getK() {
            return k;
        }

        public void setK(String k) {
            this.k = k;
        }

        @XmlAttribute
        public String getV() {
            return v;
        }

        public void setV(String v) {
            this.v = v;
        }

        @XmlElement(name = "tag-values")
        public List<ODTagValues> getTagValues() {
            return tagValues;
        }

        public void setTagValues(List<ODTagValues> tagValues) {
            this.tagValues = tagValues;
        }
    }

    /**
     * List of tag values
     */
    public static class ODTagValues {
        String id;
        String ref;

        @XmlAttribute
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @XmlAttribute
        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }
    }


    /**
     * Defines a tag values
     */
    public static class ODTagValue {
        String v;

        @XmlAttribute
        public String getV() {
            return v;
        }

        public void setV(String v) {
            this.v = v;
        }
    }
}
