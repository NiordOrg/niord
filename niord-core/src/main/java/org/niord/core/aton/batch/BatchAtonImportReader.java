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

package org.niord.core.aton.batch;

import org.niord.core.aton.vo.AtonNodeVo;
import org.niord.core.aton.vo.AtonOsmVo;
import org.niord.core.batch.AbstractItemHandler;

import javax.enterprise.context.Dependent;
import javax.inject.Named;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Reads AtoNs from an OSM xml file.
 * <p>
 * Please note, the actual aton-import.xml job file is not placed in the META-INF/batch-jobs of this project,
 * but rather, in the META-INF/batch-jobs folder of the niord-web project.<br>
 * This is because of a class-loading bug in the Wildfly implementation. See e.g.
 * https://issues.jboss.org/browse/WFLY-4988
 * <p>
 * The AtoN xml file must adhere to the OSM seamark specification; please refer to:
 * http://wiki.openstreetmap.org/wiki/Key:seamark
 * and sub-pages.
 * <p>
 * XML Example:
 * <pre>
 *   &lt;osm version='0.6' generator='JOSM'&gt;
 *     &lt;bounds minlat='34.0662408634219' minlon='-118.736715316772'
 *                maxlat='34.0731374116421' maxlon='-118.73122215271' /&gt;
 *     &lt;node id="672436827" lat="50.8070813" lon="-1.2841124" user="malcolmh" uid="128186" visible="true" version="11"
 *           changeset="9107813" timestamp="2011-08-23T21:22:36Z"&gt;
 *         &lt;tag k="seamark:buoy_cardinal:category" v="north"/&gt;
 *         &lt;tag k="seamark:buoy_cardinal:colour" v="black;yellow"/&gt;
 *         &lt;tag k="seamark:buoy_cardinal:colour_pattern" v="horizontal"/&gt;
 *         &lt;tag k="seamark:buoy_cardinal:shape" v="pillar"/&gt;
 *         &lt;tag k="seamark:light:character" v="VQ"/&gt;
 *         &lt;tag k="seamark:light:colour" v="white"/&gt;
 *         &lt;tag k="seamark:name" v="Calshot"/&gt;
 *         &lt;tag k="seamark:topmark:colour" v="black"/&gt;
 *         &lt;tag k="seamark:topmark:shape" v="2 cones up"/&gt;
 *         &lt;tag k="seamark:type" v="buoy_cardinal"/&gt;
 *     &lt;/node&gt;
 *     ...
 *   &lt;/osm&gt;
 * </pre>
 */
@Dependent
@Named("batchAtonImportReader")
public class BatchAtonImportReader extends AbstractItemHandler {

    private AtonNodeVo[] atons;
    private int atonNo = 0;

    /** {@inheritDoc} **/
    @Override
    public void open(Serializable prevCheckpointInfo) throws Exception {

        try {
            // Get hold of the data file
            Path path = batchService.getBatchJobDataFile(jobContext.getInstanceId());

            JAXBContext jaxbContext = JAXBContext.newInstance(AtonOsmVo.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            AtonOsmVo osmVo = (AtonOsmVo) unmarshaller.unmarshal(path.toFile());

            atons = osmVo.getNodes();

            if (prevCheckpointInfo != null) {
                atonNo = (Integer) prevCheckpointInfo;
            }

            getLog().info("Start processing " + atons.length + " AtoNs from index " + atonNo);

        } catch (JAXBException e) {
            getLog().log(Level.SEVERE, "Error opening aton-import data file", e);
            throw e;
        }
    }

    /** {@inheritDoc} **/
    @Override
    public Object readItem() throws Exception {
        if (atonNo < atons.length) {

            // Every now and then, update the progress
            if (atonNo % 10 == 0) {
                updateProgress((int)(100.0 * atonNo / atons.length));
            }

            getLog().info("Reading AtoN no " + atonNo);
            return atons[atonNo++];
        }
        return null;
    }

    /** {@inheritDoc} **/
    @Override
    public Serializable checkpointInfo() throws Exception {
        return atonNo;
    }
}
