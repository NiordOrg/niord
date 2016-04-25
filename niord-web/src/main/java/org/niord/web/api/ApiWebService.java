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
package org.niord.web.api;

import org.niord.model.vo.MainType;
import org.niord.model.vo.MessageVo;

import javax.ejb.Stateless;
import javax.jws.WebMethod;
import javax.jws.WebService;
import java.util.List;
import java.util.Set;

/**
 * A public web service API for accessing Niord data.
 */
@WebService
@Stateless
@SuppressWarnings("unused")
public class ApiWebService extends AbstractApiService {

    /** {@inheritDoc} */
    @WebMethod
    @Override
    public List<MessageVo> search(String language, String domain, Set<MainType> mainTypes, String wkt) throws Exception {
        return super.search(language, domain, mainTypes, wkt);
    }
}
