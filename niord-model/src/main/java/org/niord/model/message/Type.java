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
package org.niord.model.message;

/**
 * Message type
 */
public enum Type {

    // NtM types
    PERMANENT_NOTICE(MainType.NM),
    TEMPORARY_NOTICE(MainType.NM),
    PRELIMINARY_NOTICE(MainType.NM),
    MISCELLANEOUS_NOTICE(MainType.NM),
    
    // NW types
    COASTAL_WARNING(MainType.NW),
    SUBAREA_WARNING(MainType.NW),
    NAVAREA_WARNING(MainType.NW),
    LOCAL_WARNING(MainType.NW);

    MainType mainType;

    /** Constructor */
    Type(MainType mainType) {
        this.mainType = mainType;
    }

    /** Returns the main type, i.e. either NW or NM, of this type */
    public MainType getMainType() {
        return mainType;
    }
}
