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

/**
 * The Message Schedule controller
 */
angular.module('niord.schedule')

    /**
     * Interface for calling the application server
     */
    .factory('ScheduleService', [ '$http', function($http) {
        'use strict';

        return {

            /** Searches for firing areas scheduled for the given day */
            searchFiringAreas: function(params) {
                return $http.get('/rest/firing-areas/search?' + params);
            },

            /** Updates the list of firing periods for the area at the given date */
            updateFiringPeriodsForArea: function(firingArea, date) {
                return $http.put('/rest/firing-areas/firing-area?date=' + date.valueOf(), firingArea);
            }

        };
    }]);

