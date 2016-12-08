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
 * The Firing Area Schedule controller
 */
angular.module('niord.schedule')

    /**
     * Main firing area schedule controller
     */
    .controller('ScheduleCtrl', ['$scope', '$rootScope', 'growl', 'ScheduleService',
        function ($scope, $rootScope, growl, ScheduleService) {
            'use strict';

            $scope.firingAreas = [];
            $scope.state = {
                date: moment(),
                text: {
                    enabled: true,
                    query: ''
                },
                area: {
                    enabled: true,
                    areas: []
                }
            };
            $scope.domain = $rootScope.domain;


            /** Refreshes the list of firing areas **/
            $scope.loadFiringAreas = function () {
                var params = 'date=' + $scope.state.date.valueOf() + '&lang=' + $rootScope.language;

                var s = $scope.state;
                if (s.text.enabled && s.text.query.length > 0) {
                    params += '&query=' + encodeURIComponent(s.text.query);
                }
                if (s.area.enabled) {
                    angular.forEach(s.area.areas, function (area) {
                        params += '&area=' + area.id;
                    })
                }

                ScheduleService.searchFiringAreas(params)
                    .success(function (areas) {
                        $scope.firingAreas = areas;
                        $scope.checkGroupByArea();
                    });
            };

            // Monitor changes to the state
            $scope.$watch("state", $scope.loadFiringAreas, true);


            /** Changes the currently selected date with the given offset **/
            $scope.offsetDate = function (offset) {
                $scope.state.date = moment($scope.state.date).add(offset, 'days');
            };


            /** Enter edit mode for the given area **/
            $scope.edit = function (area) {
                area.editing = true;
            };


            /** Scans through the search result and marks all areas that should display an area head line **/
            $scope.checkGroupByArea = function (maxLevels) {
                maxLevels = maxLevels || 2;
                var lastAreaId = undefined;
                if ($scope.firingAreas && $scope.firingAreas.length > 0) {
                    for (var x = 0; x < $scope.firingAreas.length; x++) {
                        var area = $scope.firingAreas[x];
                        var lineage = [];
                        for (var a = area; a !== undefined; a = a.parent) {
                            lineage.unshift(a);
                        }
                        if (lineage.length > 0) {
                            a = lineage[Math.min(lineage.length - 1, maxLevels - 1)];
                            if (!lastAreaId || a.id != lastAreaId) {
                                lastAreaId = a.id;
                                area.areaHeading = a;
                            }
                        }
                    }
                }
            };
        }]);

