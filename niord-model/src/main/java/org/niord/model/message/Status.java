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
 * Defines the message status
 */
public enum Status {

    /**
     * Special status that is only used for imported message. These messages may have
     * a message number, even if they are not published.
     */
    IMPORTED,

    /**
     * Draft is the initial state of a message before it has been published
     */
    DRAFT,

    /**
     * Verified is the state of a message before it has been published
     */
    VERIFIED,

    /**
     * The state of a message that has been published, and has not yet expired
     */
    PUBLISHED,

    /**
     * The state of a message that has been published and subsequently has expired,
     * i.e. the validTo date has been passed
     */
    EXPIRED,

    /**
     * The state of a message that has been published and subsequently
     * has been manually cancelled
     */
    CANCELLED,

    /**
     * The state of a draft that has been deleted
     */
    DELETED
}
