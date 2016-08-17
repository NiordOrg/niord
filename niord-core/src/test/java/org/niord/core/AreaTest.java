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
