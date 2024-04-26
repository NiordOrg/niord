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
 * Common services.
 */
angular.module('niord.common')

    /**
     * The user service
     */
    .service('UserService', ['$rootScope', '$http',
        function ($rootScope, $http) {
            'use strict';

            return {

                /** Searches for users */
                search: function(name) {
                    return $http.get('/rest/users/search?name=' + encodeURIComponent(name));
                },

                /** Searches for user email addresses */
                searchEmails: function(name) {
                    return $http.get('/rest/users/search-emails?name=' + encodeURIComponent(name));
                }
            }
        }])
;
