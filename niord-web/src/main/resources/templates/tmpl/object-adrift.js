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

// If an observation date is defined, define the publish date interval
// and event date interval based on the observation date
if (params.get('date') !== null) {
    var date = new java.util.Date(params.get('date'));

    // Cancel message 12 hours after observation
    var offset = 12 * 60 * 60 * 1000;
    var cancelDate = new java.util.Date(params.get('date') + offset);
    params.put('cancelDate', cancelDate);

    // Update publication dates - assume manual publication, so, ignore start date
    //message.publishDateFrom = date;
    message.publishDateTo = cancelDate;

    // Update event dates
    var part = message.part(MessagePartType.DETAILS);
    if (part !== undefined) {
        var di = new org.niord.model.message.DateIntervalVo();
        di.allDay = false;
        di.fromDate = date;
        di.toDate = cancelDate;
        part.eventDates = new java.util.ArrayList();
        part.eventDates.add(di);
    }
}
