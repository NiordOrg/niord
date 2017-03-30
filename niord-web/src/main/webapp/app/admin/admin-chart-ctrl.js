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
     * ChartsAdminCtrl
     * ********************************************************************************
     * Charts Admin Controller
     * Controller for the Admin Charts page
     */
    .controller('ChartsAdminCtrl', ['$scope', 'growl', 'AdminChartService', 'DialogService', 'UploadFileService',
        function ($scope, growl, AdminChartService, DialogService, UploadFileService) {
            'use strict';

            $scope.allCharts = [];
            $scope.chart = undefined; // The chart being edited
            $scope.chartFeatureCollection = { type: 'FeatureCollection', features: [] };
            $scope.editMode = 'add';

            // Pagination
            $scope.charts = [];
            $scope.pageSize = 10;
            $scope.currentPage = 1;
            $scope.chartNo = 0;
            $scope.search = '';


            /** Loads the charts from the back-end */
            $scope.loadCharts = function() {
                $scope.chart = undefined;
                AdminChartService
                    .getCharts()
                    .success(function (charts) {
                        $scope.allCharts = charts;
                        $scope.pageChanged();
                    });
            };


            /** Returns if the string matches the given chart property */
            function match(chartProperty, str) {
                var txt = (chartProperty) ? "" + chartProperty : "";
                return txt.toLowerCase().indexOf(str.toLowerCase()) >= 0;
            }


            /** Called whenever chart pagination changes */
            $scope.pageChanged = function() {
                var search = $scope.search.toLowerCase();
                var filteredCharts = $scope.allCharts.filter(function (chart) {
                    return match(chart.chartNumber, search) ||
                        match(chart.internationalNumber, search) ||
                        match(chart.horizontalDatum, search) ||
                        match(chart.name, search);
                });
                $scope.chartNo = filteredCharts.length;
                $scope.charts = filteredCharts.slice(
                    $scope.pageSize * ($scope.currentPage - 1),
                    Math.min($scope.chartNo, $scope.pageSize * $scope.currentPage));
            };
            $scope.$watch("search", $scope.pageChanged, true);


            /** Adds a new chart **/
            $scope.addChart = function () {
                $scope.editMode = 'add';
                $scope.chart = {
                    chartNumber: undefined,
                    internationalNumber: undefined,
                    active: true,
                    horizontalDatum: 'WGS84'
                };
                $scope.chartFeatureCollection.features.length = 0;
            };


            /** Edits a chart **/
            $scope.editChart = function (chart) {
                $scope.editMode = 'edit';
                $scope.chart = angular.copy(chart);
                $scope.chartFeatureCollection.features.length = 0;
                if ($scope.chart.geometry) {
                    var feature = {type: 'Feature', geometry: $scope.chart.geometry, properties: {}};
                    $scope.chartFeatureCollection.features.push(feature);
                }
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving chart", { ttl: 5000 });
            };


            /** Saves the current chart being edited */
            $scope.saveChart = function () {

                // Update the chart geometry
                delete $scope.chart.geometry;
                if ($scope.chartFeatureCollection.features.length > 0 &&
                    $scope.chartFeatureCollection.features[0].geometry) {
                    $scope.chart.geometry = $scope.chartFeatureCollection.features[0].geometry;
                }

                if ($scope.chart && $scope.editMode === 'add') {
                    AdminChartService
                        .createChart($scope.chart)
                        .success($scope.loadCharts)
                        .error($scope.displayError);
                } else if ($scope.chart && $scope.editMode === 'edit') {
                    AdminChartService
                        .updateChart($scope.chart)
                        .success($scope.loadCharts)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given chart */
            $scope.deleteChart = function (chart) {
                DialogService.showConfirmDialog(
                    "Delete Chart?", "Delete chart number '" + chart.chartNumber + "'?")
                    .then(function() {
                        AdminChartService
                            .deleteChart(chart)
                            .success($scope.loadCharts)
                            .error($scope.displayError);
                    });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadChartsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Charts File',
                    '/rest/charts/upload-charts',
                    'json');
            };
        }]);
