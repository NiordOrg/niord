
/*
 * Copyright 2017 Danish Maritime Authority.
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

load('niord:templates/tmpl/common.js');

if (message.areas && message.areas.length > 0) {

    var seriesIds = java.util.Collections.singleton('dma-fa');
    var areaIds = java.util.Collections.singleton('' + message.areas[0].id);
    var params = new org.niord.core.message.MessageSearchParams()
        .statuses(Status.PUBLISHED)
        .seriesIds(seriesIds)
        .areaIds(areaIds);

    var firingAreaMessages = messageService.search(params).data;
    if (firingAreaMessages.length == 1) {
        msg = firingAreaMessages[0].toVo(
            org.niord.model.message.MessageVo.class,
            org.niord.core.message.Message.MESSAGE_DETAILS_FILTER);

        message.charts = msg.charts;
        message.descs = msg.descs;
        message.parts = msg.parts;
        resetGeometryIds(message);

        // Add a blank "TIME" message part to the message
        var timePart = message.checkCreatePart(MessagePartType.TIME, 0);
    }
} else {
    message.parts = [];
    message.charts = [];
}
