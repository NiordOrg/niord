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
package org.niord.core.model;

import org.niord.model.IJsonSerializable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a spatial extent
 */
@SuppressWarnings("unused")
public class Extent implements IJsonSerializable {

    double minLat, minLon, maxLat, maxLon;

    public Extent() {
    }

    public Extent(double minLat, double minLon, double maxLat, double maxLon) {
        this.minLat = minLat;
        this.minLon = minLon;
        this.maxLat = maxLat;
        this.maxLon = maxLon;
    }

    /** Checks if the given position is within this extent */
    public boolean withinExtent(double lat, double lon) {
        return lat >= minLat && lat <= maxLat &&
                lon >= minLon && lon <= maxLon;
    }

    /**
     * OpenLayers will ensure that minLon < maxLon, but then minLon may be less than -180
     * and maxLon > 180. The back-end (e.g. spatial4j) may not be able to handle this.
     * Furthermore, if minLon < 0 and maxLon > 0, there can be problems.
     * So, the extent is sliced up to avoid problems...
     *
     * @return the normalized mapExtents
     */
    public List<Extent> normalize() {
        List<Extent> extents = new ArrayList<>();

        if (minLon < -180) {
            addExtent(extents, new Extent(minLat, 360 + minLon, maxLat, 180.0));
            addExtent(extents, new Extent(minLat, -180, maxLat, maxLon));
        } else if (maxLon > 180) {
            addExtent(extents, new Extent(minLat, minLon, maxLat, 180.0));
            addExtent(extents, new Extent(minLat, -180, maxLat, maxLon - 360.0));
        } else {
            addExtent(extents, this);
        }
        return extents;
    }

    /**
     * Add the extent to the list of mapExtents
     *
     * @param extents will be the resulting list of mapExtents
     * @param extent the extent to add
     */
    private static void addExtent(List<Extent> extents, Extent extent) {
        if (extent.minLon < -0.00001 && extent.maxLon > 0.00001) {
            addExtent(extents, new Extent(extent.minLat, extent.minLon, extent.maxLat, 0.0));
            addExtent(extents, new Extent(extent.minLat, 0, extent.maxLat, extent.maxLon));
        } else {
            extents.add(extent);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Extent{" +
                "minLat=" + minLat +
                ", minLon=" + minLon +
                ", maxLat=" + maxLat +
                ", maxLon=" + maxLon +
                '}';
    }

    public double getMinLat() {
        return minLat;
    }

    public void setMinLat(double minLat) {
        this.minLat = minLat;
    }

    public double getMinLon() {
        return minLon;
    }

    public void setMinLon(double minLon) {
        this.minLon = minLon;
    }

    public double getMaxLat() {
        return maxLat;
    }

    public void setMaxLat(double maxLat) {
        this.maxLat = maxLat;
    }

    public double getMaxLon() {
        return maxLon;
    }

    public void setMaxLon(double maxLon) {
        this.maxLon = maxLon;
    }
}
