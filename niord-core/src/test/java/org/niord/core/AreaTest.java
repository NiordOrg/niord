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
package org.niord.core;

import org.junit.Test;
import org.niord.core.area.Area;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Area test
 */
public class AreaTest {

    @Test
    public void testAreaLineagePrefix() {

        List<Area> areas = new ArrayList<>();

        Area area = createArea("da", "Læsø");
        areas.add(area);
        Area parent = createArea("da", "Kattegat");
        area.setParent(parent);
        parent.setParent(createArea("da", "Danmark"));

        area = createArea("da", "Skagerak");
        areas.add(area);
        area.setParent(createArea("da", "Danmark"));

        assertEquals("Danmark. Kattegat. Skagerak. Læsø.", Area.computeAreaTitlePrefix(areas, "da"));
    }

    private Area createArea(String lang, String name) {
        Area area = new Area();
        area.checkCreateDesc(lang).setName(name);
        return area;
    }

}
