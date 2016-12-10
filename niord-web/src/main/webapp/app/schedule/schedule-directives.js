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
 * Message schedule directives.
 */
angular.module('niord.schedule')

    /**********************************************
     * Directives for editing the time schedule of
     * a message
     **********************************************/
    .directive('timeScheduleEditor', ['$rootScope', '$timeout', 'growl', 'ScheduleService', 'DateIntervalService',
        function ($rootScope, $timeout, growl, ScheduleService, DateIntervalService) {

        return {
            restrict: 'E',
            templateUrl: '/app/schedule/time-schedule-editor.html',
            replace: true,
            transclude: true,
            scope: {
                schedule:   "=",
                date:       "="
            },

            link: function (scope, element) {

                /** Adds a new time interval for the schedule **/
                scope.addTimeInterval = function () {
                    scope.schedule.editFiringPeriods.push({
                        fromDate: undefined,
                        toDate: undefined
                    });

                    // Give focus to the newly added input field
                    $timeout(function() { 
                        $(element[0]).find('input')[ (scope.schedule.editFiringPeriods.length - 1) * 2].focus()
                    });
                };


                /** Removes the time interval from the list **/
                scope.removeTimeInterval = function (index) {
                    scope.schedule.editFiringPeriods.splice(index, 1);
                };


                /** Formats the given time interval **/
                scope.formatTimeInterval = function (ti) {
                    return DateIntervalService.translateTimeInterval($rootScope.language, ti)
                };


                /** Enter edit mode for the given area **/
                scope.edit = function () {
                    var schedule = scope.schedule;
                    if (schedule.editing) {
                        schedule.editFiringPeriods = schedule.firingPeriods ? angular.copy(schedule.firingPeriods) : [];
                        if (schedule.editFiringPeriods.length == 0) {
                            scope.addTimeInterval();
                        }
                    }
                };


                /** Clears the edited list of time intervals **/
                scope.clear = function () {
                    scope.schedule.editFiringPeriods = [];
                };


                /** Cancel editing the schedule **/
                scope.cancel = function () {
                    delete scope.schedule.editFiringPeriods;
                    scope.schedule.editing = false;
                };


                /** Saves the schedule **/
                scope.save = function () {
                    scope.schedule.firingPeriods = [];

                    // Filter out dodgy time definitions
                    angular.forEach(scope.schedule.editFiringPeriods, function (ti) {
                        if (ti.fromDate === undefined && ti.toDate === undefined) {
                            return;
                        }
                        if (ti.fromDate === undefined) {
                            ti.fromDate = moment(scope.date).set({
                                hour: 0,
                                minute: 0,
                                second: 0,
                                millisecond: 0
                            });
                        } else {
                            ti.fromDate = moment(scope.date).set({
                                hour: moment(ti.fromDate).hour(),
                                minute: moment(ti.fromDate).minute(),
                                second: 0,
                                millisecond: 0
                            });
                        }
                        if (ti.toDate === undefined) {
                            ti.toDate = moment(scope.date).set({
                                hour: 23,
                                minute: 59,
                                second: 59,
                                millisecond: 0
                            });
                        } else {
                            ti.toDate = moment(scope.date).set({
                                hour: moment(ti.toDate).hour(),
                                minute: moment(ti.toDate).minute(),
                                second: 0,
                                millisecond: 0
                            });
                        }
                        scope.schedule.firingPeriods.push(ti);
                    });

                    // Save the updated schedule
                    ScheduleService.updateFiringScheduleForDate(scope.schedule, scope.date)
                        .success(function (schedule) {
                            scope.schedule.firingPeriods = schedule.firingPeriods;
                            scope.cancel();
                        })
                        .error(function() {
                            growl.error("Error updating firing periods", { ttl: 5000 });
                            scope.cancel();
                        });
                };


                // Monitor the message.editing variable
                scope.$watch("schedule.editing", scope.edit, true);
            }
        };
    }]);
