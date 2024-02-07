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
 * The admin controllers.
 */
angular.module('niord.admin')


    /**
     * ********************************************************************************
     * ScheduleAdminCtrl
     * ********************************************************************************
     * Schedule Admin Controller
     * Controller for the Admin schedules page
     */
    .controller('ScheduleAdminCtrl', ['$scope', '$rootScope', 'growl', 'AdminScheduleService', 'DialogService',
        function ($scope, $rootScope, growl, AdminScheduleService, DialogService) {
            'use strict';

            $scope.schedules = [];
            $scope.schedule = undefined; // The schedule being edited
            $scope.editMode = 'add';


            // Update the list if selectable domains
            $scope.domains = [];
            angular.forEach($rootScope.domains, function (domain) {
                if (domain.editable) {
                    $scope.domains.push(domain);
                }
            });


            /** Called when a target domain is selected **/
            $scope.messageSeriesIds = [];
            $scope.targetDomainSelected = function () {
                $scope.messageSeriesIds.length = 0;
                if ($scope.schedule) {
                    angular.forEach($rootScope.domains, function (domain) {
                        if (domain.domainId === $scope.schedule.targetDomain.domainId && domain.messageSeries) {
                            angular.forEach(domain.messageSeries, function (series) {
                                $scope.messageSeriesIds.push(series.seriesId);
                            });
                            if (domain.messageSeries.length === 1) {
                                $scope.schedule.targetSeriesId = domain.messageSeries[0].seriesId;
                            }
                        }
                    });
                }
            };


            /** Loads the schedules from the back-end */
            $scope.loadFiringSchedules = function() {
                $scope.schedule = undefined;
                AdminScheduleService
                    .getFiringSchedules()
                    .success(function (schedules) {
                        $scope.schedules = schedules;
                    });
            };


            /** Adds a new firing schedule **/
            $scope.addFiringSchedule = function () {
                $scope.editMode = 'add';
                $scope.schedule = {
                    domain: { domainId: $rootScope.domain.domainId },
                    targetDomain: { domainId: undefined },
                    targetSeriesId: undefined,
                    scheduleDays: undefined,
                    active: false
                };
            };


            /** Edits a firing schedule **/
            $scope.editFiringSchedule = function (schedule) {
                $scope.editMode = 'edit';
                $scope.schedule = angular.copy(schedule);
                $scope.schedule.domain = { domainId: $scope.schedule.domain.domainId };
                $scope.schedule.targetDomain = { domainId: $scope.schedule.targetDomain.domainId };
                $scope.targetDomainSelected();
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving firing schedule", { ttl: 5000 });
            };


            /** Saves the current firing schedule being edited */
            $scope.saveFiringSchedule = function () {

                if ($scope.editMode === 'add') {
                    AdminScheduleService
                        .createFiringSchedule($scope.schedule)
                        .success($scope.loadFiringSchedules)
                        .error($scope.displayError);
                } else if ($scope.editMode === 'edit') {
                    AdminScheduleService
                        .updateFiringSchedule($scope.schedule)
                        .success($scope.loadFiringSchedules)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given firing schedule */
            $scope.deleteFiringSchedule = function (schedule) {
                DialogService.showConfirmDialog(
                    "Delete firing schedule?", "Delete firing schedule ID '" + schedule.id + "'?")
                    .then(function() {
                        AdminScheduleService
                            .deleteFiringSchedule(schedule)
                            .success($scope.loadFiringSchedules)
                            .error($scope.displayError);
                    });
            };


            /** Updates the firing exercises **/
            $scope.updateFiringExercises = function () {
                AdminScheduleService.updateFiringExercises();
            }
        }]);
