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
package org.niord.web.api;

import org.niord.core.message.Message;
import org.niord.model.DataFilter;
import org.niord.model.message.MainType;
import org.niord.model.message.MessageVo;

import javax.ejb.Stateless;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Set;

/**
 * A public web service API for accessing Niord data.
 */
@WebService
@Stateless
@SuppressWarnings("unused")
public class ApiWebService extends AbstractApiService {

    @WebMethod
    public List<MessageVo> search(
            String language,
            Set<String> domainIds,
            Set<String> messageSeries,
            Set<String> publicationIds,
            Set<String> areaIds,
            Set<MainType> mainTypes,
            String wkt) throws Exception {

        DataFilter filter = Message.MESSAGE_DETAILS_FILTER.lang(language);

        return searchMessages(language, domainIds, messageSeries, publicationIds, areaIds, mainTypes, wkt)
                .map(m -> m.toVo(MessageVo.class, filter))
                .getData();
    }


    @WebMethod
    public MessageVo details(
            String language,
            String messageId) throws Exception {

        Message message = getMessage(messageId);

        if (message == null) {
            throw new NotFoundException("No message found with id: " + messageId);
        } else {
            DataFilter filter = Message.MESSAGE_DETAILS_FILTER.lang(language);
            return  message.toVo(MessageVo.class, filter);
        }
    }
}
