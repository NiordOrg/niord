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
    .directive('timeScheduleEditor', [function () {
        return {
            restrict: 'E',
            templateUrl: '/app/schedule/time-schedule-editor.html',
            replace: true,
            transclude: true,
            scope: {
                message:    "=",
                date:       "="
            },

            link: function (scope) {


                /** Adds a new time interval for the message **/
                scope.addTimeInterval = function () {
                    scope.message.editSchedule.push({
                        fromTime: undefined,
                        toTime: undefined
                    })
                };


                /** Cancel editing the message schedule **/
                scope.cancel = function () {
                    scope.message.editing = false;
                    delete scope.message.editSchedule;
                };


                /** Saves the message schedule **/
                scope.save = function () {
                    scope.message.schedule = scope.message.editSchedule;
                    // TODO: Save properly
                    scope.cancel();
                };

            }
        };
    }]);
