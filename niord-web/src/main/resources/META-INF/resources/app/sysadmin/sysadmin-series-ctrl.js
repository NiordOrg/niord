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
     * MessageSeriesAdminCtrl
     * ********************************************************************************
     * Message Series Admin Controller
     * Controller for the Admin message series page
     */
    .controller('MessageSeriesAdminCtrl', ['$scope', '$rootScope', '$uibModal', 'growl', 'AdminMessageSeriesService', 'DialogService',
        function ($scope, $rootScope, $uibModal, growl, AdminMessageSeriesService, DialogService) {
            'use strict';

            $scope.messageSeries = [];
            $scope.series = undefined;  // The message series being edited
            $scope.editMode = 'add';
            $scope.search = '';
            $scope.currentYear = new Date().getFullYear();

            $scope.types = {
                NW: { LOCAL_WARNING: false, COASTAL_WARNING: false, SUBAREA_WARNING: false, NAVAREA_WARNING: false },
                NM: { TEMPORARY_NOTICE: false, PRELIMINARY_NOTICE: false, PERMANENT_NOTICE: false, MISCELLANEOUS_NOTICE: false }
            };


            /** Loads the message series from the back-end */
            $scope.loadMessageSeries = function() {
                $scope.series = undefined;
                AdminMessageSeriesService
                    .getMessageSeries()
                    .success(function (messageSeries) {
                        $scope.messageSeries = messageSeries;
                    });
            };


            /** Returns if the message series automatically assigns a number **/
            $scope.numberAssigned = function (series) {
                return  series && series.numberSequenceType &&
                    series.numberSequenceType !== 'MANUAL' && series.numberSequenceType !== 'NONE';
            };


            /** Returns if the message series can define a short ID format **/
            $scope.definesShortFormat = function (series) {
                return  series && series.numberSequenceType &&
                    series.numberSequenceType !== 'NONE';
            };


            /** Update the short ID format of the message series being edited */
            $scope.updateShortFormat = function () {
                if (!$scope.definesShortFormat($scope.series)) {
                    delete $scope.series.shortFormat;
                } else {
                    $scope.series.shortFormat = $scope.series.shortFormat || '';
                }
            };


            /** Inserts the token in the short ID format field */
            $scope.insertShortFormatToken = function (token) {
                $scope.series.shortFormat += token;
                $('#shortFormat').focus();
                $scope.seriesForm.$setDirty();
            };


            /** Inserts the token in the NAVTEX preamble format field */
            $scope.insertNavtexFormatToken = function (token) {
                $scope.series.navtexFormat = $scope.series.navtexFormat || '';
                $scope.series.navtexFormat += token;
                $('#navtexFormat').focus();
                $scope.seriesForm.$setDirty();
            };


            /** Called when the main type of the edited message series is changed **/
            $scope.updateMainType = function () {
                Object.keys($scope.types['NW']).forEach(function (key) {
                    $scope.types['NW'][key] = $scope.series.types !== undefined && ($.inArray(key, $scope.series.types) !== -1);
                });
                Object.keys($scope.types['NM']).forEach(function (key) {
                    $scope.types['NM'][key] = $scope.series.types !== undefined && ($.inArray(key, $scope.series.types) !== -1);
                });
                $scope.updateShortFormat();
            };


            /** Adds a new message series **/
            $scope.addMessageSeries = function () {
                $scope.editMode = 'add';
                $scope.series = {
                    seriesId: '',
                    mainType: 'NW',
                    numberSequenceType: 'YEARLY',
                    shortFormat: '',
                    types: [],
                    excludeFromMessageIndex: false
                };
                $scope.updateMainType();
                $scope.seriesForm.$setPristine();
            };


            /** Copies a message series **/
            $scope.copyMessageSeries = function (series) {
                $scope.editMessageSeries(series);
                $scope.editMode = 'add';
                $scope.series.seriesId = undefined;
            };


            /** Edits a message series **/
            $scope.editMessageSeries = function (series) {
                $scope.editMode = 'edit';
                $scope.series = angular.copy(series);
                $scope.updateMainType();
                $scope.seriesForm.$setPristine();
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving message series", { ttl: 5000 });
            };


            /** Saves the current message series being edited */
            $scope.saveMessageSeries = function () {
                $scope.series.types = $scope.series.types || [];
                $scope.series.types.length = 0;
                Object.keys($scope.types[$scope.series.mainType]).forEach(function (key) {
                    if ($scope.types[$scope.series.mainType][key]) {
                        $scope.series.types.push(key);
                    }
                });

                if ($scope.editMode === 'add') {
                    AdminMessageSeriesService
                        .createMessageSeries($scope.series)
                        .success($scope.loadMessageSeries)
                        .error($scope.displayError);
                } else if ($scope.editMode === 'edit') {
                    AdminMessageSeriesService
                        .updateMessageSeries($scope.series)
                        .success($scope.loadMessageSeries)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given message series */
            $scope.deleteMessageSeries = function (series) {
                DialogService.showConfirmDialog(
                    "Delete message series?", "Delete message series ID '" + series.seriesId + "'?")
                    .then(function() {
                        AdminMessageSeriesService
                            .deleteMessageSeries(series)
                            .success($scope.loadMessageSeries)
                            .error($scope.displayError);
                    });
            };


            /** Edits the next number of the given message series **/
            $scope.editNextNumber = function (series) {
                $uibModal.open({
                    controller: "MessageSeriesNextNumberDialogCtrl",
                    templateUrl: "nextMessageNumberDialog.html",
                    size: 'md',
                    resolve: {
                        series: function () { return series; }
                    }
                }).result.then($scope.loadMessageSeries);
            };
        }])


    /*******************************************************************
     * Controller that handles the next-number of a message series
     *******************************************************************/
    .controller('MessageSeriesNextNumberDialogCtrl', ['$scope', 'growl', 'AdminMessageSeriesService', 'series',
        function ($scope, growl, AdminMessageSeriesService, series) {
            'use strict';

            $scope.series = series;
            $scope.data = {
                year: new Date().getFullYear(),
                years: [],
                nextNumber: undefined
            };
            for (var year = 1900; year < 2100; year++) {
                $scope.data.years.push(year);
            }

            // Loads the next-number for the selected year
            $scope.$watch("data.year", function (year) {
                AdminMessageSeriesService.getNextMessageSeriesNumber($scope.series.seriesId, year)
                    .success(function (nextNumber) {
                        $scope.data.nextNumber = nextNumber;
                    })
            });


            /** Computes the next number based on existing numbers for the given year **/
            $scope.computeNextNumber = function () {
                AdminMessageSeriesService
                    .computeNextMessageSeriesNumber($scope.series.seriesId, $scope.data.year)
                    .success(function (nextNumber) {
                        $scope.data.nextNumber = nextNumber;
                    })
            };


            /** Close the dialog and return the updated message number and year **/
            $scope.updateNextNumber = function () {
                AdminMessageSeriesService
                    .updateNextMessageSeriesNumber($scope.series.seriesId, $scope.data.year, $scope.data.nextNumber)
                    .success(function () {
                        growl.info('Next message number updated', { ttl: 3000 });
                        $scope.$close('OK');
                    })
                    .error(function () {
                        growl.error("Error updating next number", { ttl: 5000 });
                    });
            };
        }]);
