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
    .controller('ScheduleCtrl', ['$scope', '$rootScope', 'growl', 'ScheduleService', 'DialogService',
        function ($scope, $rootScope, growl, ScheduleService, DialogService) {
            'use strict';

            $scope.firingAreaPeriods = [];
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


            /** Refreshes the list of firing area schedules **/
            $scope.loadFiringAreaPeriods = function () {
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

                ScheduleService.searchFiringAreaPeriods(params)
                    .success(function (firingAreaPeriods) {
                        $scope.firingAreaPeriods = firingAreaPeriods;
                        $scope.firingAreas = firingAreaPeriods.map(function (fap) { return fap.area; });
                        $scope.checkGroupByArea();
                    });
            };

            // Monitor changes to the state
            $scope.$watch("state", $scope.loadFiringAreaPeriods, true);


            /** Changes the currently selected date with the given offset **/
            $scope.offsetDate = function (offset) {
                $scope.state.date = moment($scope.state.date).add(offset, 'days');
            };


            /** Enter edit mode for the given area **/
            $scope.edit = function (firingAreaPeriods) {
                firingAreaPeriods.editing = true;
            };


            /** Updates the firing exercises **/
            $scope.updateFiringExercises = function () {
                DialogService.showConfirmDialog(
                    'Update Firing Exercises',
                    'Firing exercises are automatically updated from the schedule every night.\n' +
                    'Do you wish to update them now?')
                    .then(function() {
                        ScheduleService.updateFiringExercises()
                            .success(function () {
                                growl.info('Firing Exercises have been updated', { ttl: 3000 });
                            })
                            .error(function () {
                                growl.error('Error updating firing Exercises', { ttl: 5000 });
                            });
                    });
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
                            if (!lastAreaId || a.id !== lastAreaId) {
                                lastAreaId = a.id;
                                area.areaHeading = a;
                            }
                        }
                    }
                }
            };
        }]);

