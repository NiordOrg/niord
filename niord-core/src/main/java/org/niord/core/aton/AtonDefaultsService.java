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
package org.niord.core.aton;

import io.quarkus.runtime.StartupEvent;
import org.apache.commons.lang.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import javax.management.RuntimeErrorException;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;
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
 *     &lt;tag k="seamark:topmark:colour_pattern" v=""/&gt;
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
@ApplicationScoped
@SuppressWarnings("unused")
public class AtonDefaultsService {

    /**
     * The default logger.
     */
    @Inject
    private Logger log;

    /**
     * The INT-1-Preset default parsing rules.
     */
    @ConfigProperty(name = "niord.aton-defaults.int-1-preset.parser", defaultValue = "aton/aton-osm-defaults.xslt")
    private String defaultsParsing;

    /**
     * The IALA System to be skipped during the defaults parsing.
     */
    @ConfigProperty(name = "niord.aton-defaults.int-1-preset.iala-skip-system", defaultValue = "IALA-A")
    private IalaBuoyageSystem ialaSkipSystem;

    /**
     * The file name of the INT-1-Present configuration XML.
     */
    @ConfigProperty(name = "niord.aton-defaults.int-1-preset.fileName", defaultValue = "aton/INT-1-preset.xml")
    private String int1Preset;

    /**
     * Additional configuration files to append information to the INT-1-present
     * configuration.
     */
    @ConfigProperty(name = "niord.aton-defaults.int-1-preset.extensions")
    private Optional<List<String>> getInt1PresetExts;

    private OsmDefaults osmDefaults;

    // Look-up table for tag-values
    private Map<String, ODTagValues> osmTagValues = new HashMap<>();
    private Map<String, ODNodeType> osmNodeTypes = new HashMap<>();


    /** Called upon application startup */
    public void init(@Observes StartupEvent ev) {
        // In order not to stall webapp deployment, wait 3 seconds before initializing the defaults
        new java.util.Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        generateDefaults();
                    }
                },
                3000
        );
    }
    
    public static void main(String[] args) {
        AtonDefaultsService ads = new AtonDefaultsService();
        ads.log = LoggerFactory.getLogger("foo");
        ads.defaultsParsing = "/aton/aton-osm-defaults.xslt";
        ads.int1Preset = "/aton/INT-1-preset.xml";
        ads.generateDefaults();
        
        
        System.out.println("NYE");
    }

    /**
     * Generates the AtoN defaults from the INT-1-preset.xml file
     */
    private void generateDefaults() {
        try {
            final long t0 = System.currentTimeMillis();
            final ClassLoader classloader = Thread.currentThread().getContextClassLoader();
            InputStream is = classloader.getResourceAsStream(this.defaultsParsing);
            if (is == null) {
                throw new RuntimeException("Could not read resource " + this.defaultsParsing);
            }
            final Source xsltSource = new StreamSource(is);
            is = classloader.getResourceAsStream(this.int1Preset);
            if (is == null) {
                throw new RuntimeException("Could not read resource " + this.int1Preset);
            }
            final Source xmlSource = new StreamSource(is);

            // Capture the generated xml as a string
            StringWriter xml = new StringWriter();
            Result result = new StreamResult(xml);

            // Execute the xslt
            TransformerFactory transFact = TransformerFactory.newInstance();
            Transformer trans = transFact.newTransformer(xsltSource);
            trans.setParameter("ialaSkipSystem", this.ialaSkipSystem.other().toString());
            trans.transform(xmlSource, result);

            // Fix spelling mistakes
            String resultXml = xml.toString();
            resultXml = resultXml
                    .replace("topamrk", "topmark")
                    .replace("patern", "pattern")
                    .replace("Supplimentary", "Supplementary");

            // Read in the result as OsmDefaults data
            JAXBContext jaxbContext = JAXBContext.newInstance(OsmDefaults.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            this.osmDefaults = (OsmDefaults) unmarshaller.unmarshal(new StringReader(resultXml));

            // Build look-up tables for fast access
            this.osmDefaults.getTagValues().stream()
                    .forEach(tv -> this.osmTagValues.put(tv.getId(), tv));
            this.osmDefaults.getNodeTypes().stream()
                    .forEach(nt -> this.osmNodeTypes.put(nt.getName(), nt));

            log.trace("Created AtoN defaults in " + (System.currentTimeMillis() - t0) +  " ms");

            // Now the same for the INT-1-Preset extensions
            if(getInt1PresetExts.isPresent()) {
                this.getInt1PresetExts.get()
                        .stream()
                        .map(classloader::getResourceAsStream)
                        .map(StreamSource::new)
                        .map(source -> {
                            // Parse each of the extensions separately
                            StringWriter xmlExt = new StringWriter();
                            Result resultExt = new StreamResult(xmlExt);
                            try {
                                trans.transform(source, resultExt);
                                return (OsmDefaults) unmarshaller.unmarshal(new StringReader(xmlExt.toString()));
                            } catch (TransformerException | JAXBException ex) {
                                this.log.error("Failed creating AtoN extension in " + ex, ex);
                                return null;
                            }
                        })
                        .forEach(osmDefaultsExt -> {
                            // Update look-up tables for fast access
                            osmDefaultsExt.getTagValues()
                                    .stream()
                                    .forEach(tv -> {
                                        if(osmTagValues.containsKey(tv.getId())) {
                                            osmTagValues.get(tv.getId()).getTags().addAll(tv.getTags());
                                        } else {
                                            osmTagValues.put(tv.getId(), tv);
                                        }
                                    });
                            osmDefaultsExt.getNodeTypes()
                                    .stream()
                                    .forEach(nt -> {
                                        if(osmNodeTypes.containsKey(nt.getName())) {
                                            osmNodeTypes.get(nt.getName()).getTags().addAll(nt.getTags());
                                        } else {
                                            osmNodeTypes.put(nt.getName(), nt);
                                        }
                                    });
                        });
                log.trace("Added AtoN S-125 extension defaults in " + (System.currentTimeMillis() - t0) + " ms");
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        return this.osmNodeTypes.values().stream()
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
        if (aton == null || StringUtils.isBlank(nodeTypeName) || !this.osmNodeTypes.containsKey(nodeTypeName)) {
            return;
        }

        this.osmNodeTypes.get(nodeTypeName)
                .getTags()
                .forEach(tag -> {
                    if (aton.getTagValue(tag.getK()) == null) {
                        String v = StringUtils.defaultString(tag.getV());
                        aton.getTags().add(new AtonTag(tag.getK(), v));
                    }
                });
    }


    /**
     * Describes the given AtoN with the tags of the node type with the given
     * name.
     *
     * @param aton the AtoN to be described
     * @param nodeType the node type
     */
    public List<AtonTagMeta> describeAtonForNodeTypes(AtonNode aton, String nodeType) {
        // Sanity checks
        if (aton == null || StringUtils.isBlank(nodeType)) {
            return Collections.emptyList();
        }

        // Describe the AtoN tags by the type
        return Optional.of(nodeType)
                .map(this::getNodesByType)
                .orElse(Collections.emptyList())
                .stream()
                .flatMap(type -> type.getTags().stream())
                .map(tag -> {
                    String def = StringUtils.capitalize(tag.getK().split(":")[tag.getK().split(":").length - 1]);
                    String text = StringUtils.defaultString(StringUtils.trimToNull(tag.getText()), def);
                    return new AtonTagMeta(tag.getK(), text, tag.getType());
                })
                .collect(Collectors.toList());
    }

    /**
     * Describes the selected AtoN tags based on the provided keys that should
     * match the tag entries loaded.
     * name.
     *
     * @param tagKeys the keys of the tags to be described
     */
    public List<AtonTagMeta> describeAtonForTagKeys(List<String> tagKeys) {
        // Sanity checks
        if (tagKeys == null || tagKeys.isEmpty()) {
            return Collections.emptyList();
        }

        // Describe the AtoN tags by the type
        return this.osmNodeTypes.values().stream()
                .map(ODNodeType::getTags)
                .flatMap(List::stream)
                .distinct()
                .filter(tag -> tagKeys.contains(tag.getK()))
                .map(tag -> {
                    String def = StringUtils.capitalize(tag.getK().split(":")[tag.getK().split(":").length - 1]);
                    String text = StringUtils.defaultString(StringUtils.trimToNull(tag.getText()), def);
                    return new AtonTagMeta(tag.getK(), text, tag.getType());
                })
                .collect(Collectors.toList());
    }


    /**
     * Computes an auto-complete list for OSM tag keys, based on the current AtoN and key
     *
     * @param aton the current AtoN
     * @param keyStr the currently typed key
     * @param maxKeyNo the max number of keys to return
     * @return the auto-complete list
     */
    public List<String> computeKeysForAton(AtonNode aton, String keyStr, int maxKeyNo) {

        // Return empty result for empty key string
        if (StringUtils.isBlank(keyStr)) {
            return Collections.emptyList();
        }

        List<ODNodeType> matchingNodeTypes = computeMatchingNodeTypes(aton);
        Set<String> existingTagKeys = aton.getTags().stream()
                .map(AtonTag::getK)
                .collect(Collectors.toSet());

        // Filter the tag keys of the matching node types, such that
        // 1) There is a substring match with the "key" param
        // 2) The key is not already defined in the AtoN
        List<String> result = matchingNodeTypes.stream()
                .flatMap(nt -> nt.getTags().stream())
                .map(ODTag::getK)
                .filter(k -> StringUtils.containsIgnoreCase(k, keyStr))
                .filter(k -> !existingTagKeys.contains(k))
                .distinct()
                .limit(maxKeyNo)
                .collect(Collectors.toList());

        // If there is no match from the matching node types, just look for any matching tag key
        if (result.isEmpty()) {
            result = this.osmNodeTypes.values().stream()
                    .flatMap(nt -> nt.getTags().stream())
                    .map(ODTag::getK)
                    .filter(k -> StringUtils.containsIgnoreCase(k, keyStr))
                    .distinct()
                    .limit(maxKeyNo)
                    .sorted()
                    .collect(Collectors.toList());
        }

        return result;
    }


    /**
     * Creates an auto-complete list for OSM tag values, based on the current AtoN, key and value
     *
     * @param aton the current AtoN
     * @param key the current key
     * @param valueStr the currently typed value
     * @param maxValueNo the max number of values to return
     * @return the auto-complete list
     */
    public List<String> getValuesForAtonAndKey(AtonNode aton, String key, String valueStr, int maxValueNo) {

        // Return empty result for empty key
        if (StringUtils.isBlank(key)) {
            return Collections.emptyList();
        }

        // Find a tag with a matching key in the set of node types that matches the AtoN
        List<ODNodeType> matchingNodeTypes = computeMatchingNodeTypes(aton);
        List<String> values = matchingNodeTypes.stream()
                .map(nt -> computeValuesForNodeType(nt, key, valueStr))
                .filter(v -> !v.isEmpty())
                .flatMap(Collection::stream)
                .distinct()
                .limit(maxValueNo)
                .collect(Collectors.toList());

        // If we did not find any matching key-value in the set of node types
        // that matches the AtoN, look for any matching key-value
        if (values.isEmpty()) {
            values = this.osmNodeTypes.values().stream()
                    .map(nt -> computeValuesForNodeType(nt, key, valueStr))
                    .filter(v -> !v.isEmpty())
                    .flatMap(Collection::stream)
                    .distinct()
                    .limit(maxValueNo)
                    .collect(Collectors.toList());
        }

        return values;
    }


    /**
     * Computes the tag values for the given node type, key and value substring.
     * @param nodeType the node type
     * @param key the tag key
     * @param valueStr the value string
     * @return the tag values for the given node type, key and value substring
     */
    private List<String> computeValuesForNodeType(ODNodeType nodeType, String key, String valueStr) {
        ODTag tag = nodeType.tag(key);
        return computeValuesForTag(tag).stream()
                .filter(v -> valueStr == null || StringUtils.containsIgnoreCase(v, valueStr))
                .distinct()
                .collect(Collectors.toList());
    }


    /**
     * Compute the list of values for the given tag. The tag can have various formats:
     *
     * <p>
     * Format 1) Tag defines the value in "v" attribute
     * <pre>
     *     &lt;tag k="seamark:notice:function" v="information"/&gt;
     * </pre>
     * <p>
     * Format 2) Tag defines values in "tag-value" sub-elements
     * <pre>
     *     &lt;tag k="seamark:buoy_lateral:category"&gt;
     *         &lt;tag-value v="danger_right"/&gt;
     *         &lt;tag-value v="junction_right"/&gt;
     *         &lt;tag-value v="turnoff_right"/&gt;
     *         &lt;tag-value v="harbour_right"/&gt;
     *     &lt;/tag&gt;
     * </pre>
     * Format 3) defines values in "tag-values" value list references
     * <pre>
     *     &lt;tag k="seamark:buoy_lateral:colour"&gt;
     *         &lt;tag-values ref="rightlateralcolours"/&gt;
     *         &lt;tag-values ref="leftlateralcolours"/&gt;
     *     &lt;/tag&gt;
     * </pre>
     * <p>
     *
     * @param tag the tag to find the values for
     * @return the list of values
     */
    private List<String> computeValuesForTag(ODTag tag) {
        if (tag == null) {
            return Collections.emptyList();

        } else if (StringUtils.isNotBlank(tag.getV())) {
            // The tag explicitly defines the value in the "v" attribute
            return Collections.singletonList(tag.getV());
        }

        // Look for "<tag-values ref="id"/>" sub-elements
        if (tag.getTagValueRefs() != null) {
            return tag.getTagValueRefs().stream()
                    .filter(tvs -> tvs.getRef() != null && this.osmTagValues.containsKey(tvs.getRef()))
                    .map(tvs -> this.osmTagValues.get(tvs.getRef()))
                    .filter(tvs -> tvs != null && (tvs.getTags() != null || tvs.getTagValues() != null))
                    .map(this::computeValuesForTagValues)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

        } else if (tag.getTagValues() != null) {
            // Look for "<tag-value v="value"/>" sub-elements
            return tag.getTagValues().stream()
                    .map(ODTagValue::getV)
                    .collect(Collectors.toList());
        }

        // No joy
        return Collections.emptyList();
    }

    /**
     * Computes the list of values from an ODTagValues object in an iterative
     * manner so that we can also capture references of references.
     *
     * @param tagValues tha tag values to find the values for
     * @return the list of values
     */
    private List<String> computeValuesForTagValues(ODTagValues tagValues) {
        if (tagValues == null) {
            return Collections.emptyList();

        }

        if(tagValues.getTagValues() != null && !tagValues.getTagValues().isEmpty()) {
            return tagValues.getTagValues().stream()
                    .map(this::computeValuesForTagValues)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } else if(tagValues.getRef() != null) {
            return Optional.of(tagValues.getRef())
                    .map(this.osmTagValues::get)
                    .map(this::computeValuesForTagValues)
                    .orElse(Collections.emptyList());
        } else if(tagValues.getTags() != null && !tagValues.getTags().isEmpty()) {
            return tagValues.getTags().stream()
                    .map(ODTagValue::getV)
                    .collect(Collectors.toList());
        }

        // No joy
        return Collections.emptyList();
    }

    /**
     * Returns a list of matching node types for the given AtoN, sorted
     * so that the first node types have a higher match with the AtoN.
     *
     * @param aton the AtoN
     * @return a list of matching node types for the given AtoN
     */
    private List<ODNodeType> computeMatchingNodeTypes(AtonNode aton) {

        // Compute an AtoN match score for each node type
        Map<ODNodeType, Integer> nodeTypeScore = this.osmNodeTypes.values().stream()
            .collect(Collectors.toMap(Function.identity(), nt -> computeNodeTypeMatch(aton, nt)));

        // Returns all node types with a non-trivial match (score > 2) sorted by the score
        return nodeTypeScore.keySet().stream()
                .filter(nt -> nodeTypeScore.get(nt) > 2)
                .sorted((nt1, nt2) -> nodeTypeScore.get(nt2).compareTo(nodeTypeScore.get(nt1)))
                .collect(Collectors.toList());
    }

    /**
     * Returns a score for match between the AtoN and the node type
     *
     * @param aton the AtoN
     * @param nodeType the node type
     * @return a score for match between the AtoN and the node type
     */
    private int computeNodeTypeMatch(AtonNode aton, ODNodeType nodeType) {
        int score = 0;

        // Check for matching tag values
        score += nodeType.getTags().stream()
                .mapToInt(t -> {
                    AtonTag tag = aton.getTag(t.getK());
                    if (tag != null && Objects.equals(tag.getV(), t.getV())) {
                        if ("seamark:type".equals(t.getK())) {
                            return 10;  // Matching "seamark:type" value
                        } else {
                            return 4;   // Matching key and value
                        }
                    } else if (tag != null) {
                        return 2;       // Matching key
                    } else {
                        return 0;       // No match
                    }
                })
                .sum();

        return score;
    }

    /**
     * Tries to find a node type by the type key, as this is defined in the
     * "seamark:type" tag of the node.
     *
     * @param type the type of the AtoN node
     * @return The matching node type
     */
    private List<ODNodeType> getNodesByType(String type) {
        return this.osmNodeTypes.values().stream()
                .filter(t -> t.tag("seamark:type") != null)
                .filter(t -> Objects.equals(t.tag("seamark:type").v, type))
                .collect(Collectors.toList());
    }

    /*************************/
    /** Helper classes      **/
    /*************************/

    /**
     * Root element in the OSM Defaults data generated by the aton-osm-defaults.xslt transformation
     */
    @XmlRootElement(name = "osm-defaults")
    private static class OsmDefaults {

        List<ODTagValues> tagValues = new ArrayList<>();
        List<ODNodeType> nodeTypes;

        @XmlElement(name = "tag-values")
        List<ODTagValues> getTagValues() {
            return tagValues;
        }

        public void setTagValues(List<ODTagValues> tagValues) {
            this.tagValues = tagValues;
        }

        @XmlElement(name = "node-type")
        List<ODNodeType> getNodeTypes() {
            return nodeTypes;
        }

        public void setNodeTypes(List<ODNodeType> nodeTypes) {
            this.nodeTypes = nodeTypes;
        }
    }

    /**
     * Defines a node type
     */
    private static class ODNodeType {
        String name;
        List<ODTag> tags = new ArrayList<>();

        /** Returns if the node type contains a matching tag key pattern and value */
        public boolean hasTagValue(String k, String v) {
            return tags.stream().anyMatch(t -> t.getK().matches(k) && v.equals(t.getV()));
        }

        /** Returns the tag with the given key, or null if not found */
        public ODTag tag(String key) {
            return tags.stream()
                    .filter(t -> t.getK().equals(key))
                    .findFirst()
                    .orElse(null);
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
    @SuppressWarnings("unused")
    private static class ODTag {
        String k;
        String v;
        String text;
        String type;
        List<ODTagValues> tagValueRefs;
        List<ODTagValue> tagValues;

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

        @XmlAttribute
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @XmlAttribute
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @XmlElement(name = "tag-values")
        List<ODTagValues> getTagValueRefs() {
            return tagValueRefs;
        }

        public void setTagValueRefs(List<ODTagValues> tagValueRefs) {
            this.tagValueRefs = tagValueRefs;
        }

        @XmlElement(name = "tag-value")
        List<ODTagValue> getTagValues() {
            return tagValues;
        }

        public void setTagValues(List<ODTagValue> tagValues) {
            this.tagValues = tagValues;
        }
    }

    /**
     * List of tag values
     */
    @SuppressWarnings("unused")
    private static class ODTagValues {
        String id;
        String ref;
        List<ODTagValue> tags;
        List<ODTagValues> tagValues = new ArrayList<>();

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

        @XmlElement(name = "tag-value")
        public List<ODTagValue> getTags() {
            return tags;
        }

        public void setTags(List<ODTagValue> tags) {
            this.tags = tags;
        }

        @XmlElement(name = "tag-values")
        List<ODTagValues> getTagValues() {
            return tagValues;
        }

        public void setTagValues(List<ODTagValues> tagValues) {
            this.tagValues = tagValues;
        }
    }


    /**
     * Defines a tag values
     */
    private static class ODTagValue {
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
