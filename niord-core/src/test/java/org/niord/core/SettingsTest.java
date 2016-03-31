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
import org.niord.core.settings.SettingValueExpander;

import static org.junit.Assert.assertEquals;

/**
 * Settings test
 */
public class SettingsTest {

    @Test
    public void testSettingValueExpander() {

        SettingValueExpander sve = new SettingValueExpander("${a}${b}${c}");
        assertEquals("a", sve.nextToken());
        sve.replaceToken(sve.nextToken(), "A");
        assertEquals("b", sve.nextToken());
        sve.replaceToken(sve.nextToken(), "B");
        assertEquals("c", sve.nextToken());
        sve.replaceToken(sve.nextToken(), "C");
        assertEquals("ABC", sve.getValue());

        // Test token nesting
        sve = new SettingValueExpander("xyz ${a} 123");
        sve.replaceToken(sve.nextToken(), "xxx${b}yyy");
        assertEquals("b", sve.nextToken());
    }
}
