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
@XmlJavaTypeAdapter(value = Iso8601DateXmlAdapter.class, type = Date.class)
package org.niord.model.vo.aton;

import java.util.Date;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Placing this file in the org.niord.model.vo.aton package ensures that the
 * java.util.Date class in the org.niord.model.vo.aton package gets processed by
 * org.niord.model.vo.aton.Iso8601DateXmlAdapter
 */
