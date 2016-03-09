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

import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;

/**
 * Creates default OSM node-tag sets based on INT-1-Presets.xml based on:
 * https://raw.githubusercontent.com/OpenSeaMap/josm/master/INT-1-preset.xml
 *
 * <p>
 * NB: Remove the "xmlns" attribute from the root &lt;presets&gt; element before checking the file into this project.
 * <p>
 * The XML file is transformed to a more manageable format for relevant node types, via the aton-osm-defaults.xslt
 * transformation.
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class AtonDefaultsService {

    @Inject
    private Logger log;

    @PostConstruct
    public void init() {

        try {
            long t0 = System.currentTimeMillis();
            Source xsltSource = new StreamSource(getClass().getResourceAsStream("/aton/aton-osm-defaults.xslt"));
            Source xmlSource = new StreamSource(getClass().getResourceAsStream("/aton/INT-1-preset.xml"));

            // send the result to a file
            File resultFile = File.createTempFile("aton-osm-defaults", ".xml");
            Result result = new StreamResult(resultFile);

            TransformerFactory transFact = TransformerFactory.newInstance();
            Transformer trans = transFact.newTransformer(xsltSource);
            trans.transform(xmlSource, result);
            log.info("********* Created AtoN defaults " + resultFile + " in " + (System.currentTimeMillis() - t0) +  " ms");
        } catch (Exception e) {
            log.error("Failed creating AtoN defaults in " + e, e);
        }

    }

}
