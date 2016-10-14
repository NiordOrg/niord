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
            $scope.autoAssigned = function (series) {
                return  series && series.numberSequenceType &&
                    series.numberSequenceType != 'MANUAL' && series.numberSequenceType != 'NONE';
            };


            /** Update the short ID format of the message series being edited */
            $scope.updateShortFormat = function () {
                if (!$scope.autoAssigned($scope.series)) {
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


            /** Inserts the token in the publish tag format field */
            $scope.insertTagFormatToken = function (index, token) {
                $scope.series.publishTagFormats[index] += token;
                $('#tagFormat' + index).focus();
                $scope.seriesForm.$setDirty();
            };


            /** Adds a new blank publish tag format after the given index **/
            $scope.addPublishTagFormat = function (index) {
                $scope.series.publishTagFormats.splice(index + 1, 0, '');
            };


            /** Removes the publish tag format at the given index **/
            $scope.removePublishTagFormat = function (index) {
                $scope.series.publishTagFormats.splice(index, 1);
                if ($scope.series.publishTagFormats.length == 0) {
                    $scope.addPublishTagFormat(0);
                }
            };


            /** Adds a new message series **/
            $scope.addMessageSeries = function () {
                $scope.editMode = 'add';
                $scope.series = {
                    seriesId: '',
                    mainType: 'NW',
                    numberSequenceType: 'YEARLY',
                    shortFormat: '',
                    publishTagFormats: [ '' ]
                };
                $scope.updateShortFormat();
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
                $scope.updateShortFormat();
                if (!$scope.series.publishTagFormats) {
                    $scope.series.publishTagFormats = [];
                }
                $scope.series.publishTagFormats.push('');
                $scope.seriesForm.$setPristine();
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving message series", { ttl: 5000 });
            };


            /** Saves the current message series being edited */
            $scope.saveMessageSeries = function () {
                if ($scope.series && $scope.editMode == 'add') {
                    AdminMessageSeriesService
                        .createMessageSeries($scope.series)
                        .success($scope.loadMessageSeries)
                        .error($scope.displayError);
                } else if ($scope.series && $scope.editMode == 'edit') {
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
