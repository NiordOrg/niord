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
package org.niord.josm.seachart;

/**
 * The existing JOSM seachart code has been modified to avoid calls to System.exit().
 * Instead, this RuntimeException will be thrown
 */
@SuppressWarnings("unused")
public class SeachartException extends RuntimeException {

    public SeachartException() {
    }

    public SeachartException(String message) {
        super(message);
    }

    public SeachartException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeachartException(Throwable cause) {
        super(cause);
    }
}
