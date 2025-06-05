/*
 * Copyright (c) 2024 GLA Research and Development Directorate
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.s100.s124;

import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.niord.core.geojson.Feature;
import org.niord.core.geojson.FeatureCollection;
import org.niord.core.message.Message;
import org.niord.core.message.MessagePart;

import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.BoundingShapeType;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.EnvelopeType;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.Pos;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.impl.BoundingShapeTypeImpl;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.impl.EnvelopeTypeImpl;
import dk.dma.niord.s100.xmlbindings.s100.gml.profiles._5_0.impl.PosImpl;

class S124DatasetBuilder {

    /**
     * For easy generation of the bounding shapes for the dataset or individual features, we are using this function.
     *
     * @param atonNodes
     *            The messa to generate the bounding shape from
     * @return the bounding shape
     */
    static BoundingShapeType generateBoundingShape(MessagePart messagePart) {
        // Calculate the bounding by envelope
        final Envelope envelope = new Envelope();
        FeatureCollection fc = messagePart.getGeometry();
        if (fc != null && fc.getFeatures() != null) {
            for (Feature f : fc.getFeatures()) {
                enclosingEnvelopeFromGeometry(envelope, f.getGeometry());
            }
        }

        Pos lowerCorner = new PosImpl();
        // Envelope uses X,Y (lon,lat) but GML expects lat,lon order
        lowerCorner.setValue(new Double[] { envelope.getMinY(), envelope.getMinX() });
        Pos upperCorner = new PosImpl();
        upperCorner.setValue(new Double[] { envelope.getMaxY(), envelope.getMaxX() });

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

    /**
     * For easy generation of the bounding shapes for the dataset or individual features, we are using this function.
     *
     * @param atonNodes
     *            The AtoN nodes to generate the bounding shape from
     * @return the bounding shape
     */
    static BoundingShapeType generateBoundingShape(Iterable<Message> messages) {
        // Calculate the bounding by envelope
        final Envelope envelope = new Envelope();
        for (Message m : messages) {
            for (MessagePart p : m.getParts()) {
                FeatureCollection fc = p.getGeometry();
                if (fc != null && fc.getFeatures() != null) {
                    for (Feature f : fc.getFeatures()) {
                        enclosingEnvelopeFromGeometry(envelope, f.getGeometry());
                    }
                }
            }
        }

        Pos lowerCorner = new PosImpl();
        // Envelope uses X,Y (lon,lat) but GML expects lat,lon order
        lowerCorner.setValue(new Double[] { envelope.getMinY(), envelope.getMinX() });
        Pos upperCorner = new PosImpl();
        upperCorner.setValue(new Double[] { envelope.getMaxY(), envelope.getMaxX() });

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

    /**
     * Adds the enclosing geometry boundaries to the provided envelop.
     *
     * @param envelope
     *            The envelope to be updated
     * @param geometry
     *            The geometry to update the envelope boundaries with
     * @return the updates envelope
     */
    static Envelope enclosingEnvelopeFromGeometry(Envelope envelope, Geometry geometry) {
        final Geometry enclosingGeometry = geometry.getEnvelope();
        final Coordinate[] enclosingCoordinates = enclosingGeometry.getCoordinates();
        for (Coordinate c : enclosingCoordinates) {
            envelope.expandToInclude(c);
        }
        return envelope;
    }

    /**
     * A helpful utility to generate the dataset ID in a single place based on the MRN prefix provided and the dataset UUID.
     * <p/>
     * The resulting ID should follow an MRN structure. Therefore, an MRN prefix will be used and the UUID identified for
     * this dataset will be appended in the end. If no MRN prefix is provided, then a standard one to make sure it conforms
     * to something like an MRN will be used:
     * <p/>
     * e.g. "urn:mrn:test:s125:dataset"
     *
     * @param prefix
     *            the MRN prefix to be used
     * @param uuid
     *            the UUID of the dataset
     * @return the combine dataset ID as an MRN that includes the dataset UUID
     */
    static String generateDatasetId(String prefix, UUID uuid) {
        return Optional.ofNullable(prefix).filter(StringUtils::isNotBlank).map(p -> p.endsWith(":") ? p : p + ":").orElse("urn:mrn:test:s124:")
                + Optional.ofNullable(uuid).orElse(UUID.randomUUID());
    }
}
