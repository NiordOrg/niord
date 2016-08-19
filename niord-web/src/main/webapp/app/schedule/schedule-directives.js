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
    .directive('timeScheduleEditor', ['$rootScope', '$timeout', 'DateIntervalService',
        function ($rootScope, $timeout, DateIntervalService) {

        return {
            restrict: 'E',
            templateUrl: '/app/schedule/time-schedule-editor.html',
            replace: true,
            transclude: true,
            scope: {
                message:    "=",
                date:       "="
            },

            link: function (scope, element) {

                /** Adds a new time interval for the message **/
                scope.addTimeInterval = function () {
                    scope.message.editSchedule.push({
                        fromTime: undefined,
                        toTime: undefined
                    });

                    // Give focus to the newly added input field
                    $timeout(function() { 
                        $(element[0]).find('input')[ (scope.message.editSchedule.length - 1) * 2].focus()
                    });
                };


                /** Removes the time interval from the list **/
                scope.removeTimeInterval = function (index) {
                    scope.message.editSchedule.splice(index, 1);
                };


                /** Formats the given time interval **/
                scope.formatTimeInterval = function (ti) {
                    return DateIntervalService.translateTimeInterval($rootScope.language, ti)
                };


                /** Enter edit mode for the given message **/
                scope.edit = function () {
                    var msg = scope.message;
                    if (msg.editing) {
                        msg.editSchedule = msg.schedule ? angular.copy(msg.schedule) : [];
                        if (msg.editSchedule.length == 0) {
                            scope.addTimeInterval();
                        }
                    }
                };


                /** Clears the edited list of time intervals **/
                scope.clear = function () {
                    scope.message.editSchedule = [];
                };


                /** Cancel editing the message schedule **/
                scope.cancel = function () {
                    delete scope.message.editSchedule;
                    scope.message.editing = false;
                };


                /** Saves the message schedule **/
                scope.save = function () {
                    scope.message.schedule = [];

                    // Filter out dodgy time definitions
                    angular.forEach(scope.message.editSchedule, function (ti) {
                        if (ti.fromTime === undefined && ti.toTime === undefined) {
                            return;
                        }
                        if (ti.fromTime === undefined) {
                            ti.fromTime = moment(scope.date).set({
                                hour: 0,
                                minute: 0,
                                second: 0,
                                millisecond: 0
                            });
                        } else {
                            ti.fromTime = moment(scope.date).set({
                                hour: moment(ti.fromTime).hour(),
                                minute: moment(ti.fromTime).minute(),
                                second: 0,
                                millisecond: 0
                            });
                        }
                        if (ti.toTime === undefined) {
                            ti.toTime = moment(scope.date).set({
                                hour: 23,
                                minute: 59,
                                second: 59,
                                millisecond: 0
                            });
                        } else {
                            ti.toTime = moment(scope.date).set({
                                hour: moment(ti.toTime).hour(),
                                minute: moment(ti.toTime).minute(),
                                second: 0,
                                millisecond: 0
                            });
                        }
                        scope.message.schedule.push(ti);
                    });

                    // TODO: Save properly

                    scope.cancel();
                };


                // Monitor the message.editing variable
                scope.$watch("message.editing", scope.edit, true);
            }
        };
    }]);
