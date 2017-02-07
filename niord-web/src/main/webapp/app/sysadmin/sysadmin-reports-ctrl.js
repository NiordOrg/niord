/*
 * Copyright 2017 Danish Maritime Authority.
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
     * ReportsAdminCtrl
     * ********************************************************************************
     * Reports Admin Controller
     * Controller for the Admin Reports page
     */
    .controller('ReportsAdminCtrl', ['$scope', '$rootScope', '$uibModal', 'growl', 'AdminReportService', 'DialogService',
        function ($scope, $rootScope, $uibModal, growl, AdminReportService, DialogService) {
            'use strict';

            $scope.reports = [];
            $scope.report = undefined;  // The report being edited
            $scope.editMode = 'add';
            $scope.search = '';


            /** Loads the report from the back-end */
            $scope.loadReports = function() {
                $scope.report = undefined;
                AdminReportService
                    .getReports()
                    .success(function (reports) {
                        $scope.reports = reports;
                    });
            };


            /** Adds a new report **/
            $scope.addReport = function () {
                $scope.editMode = 'add';
                $scope.report = {
                    reportId: '',
                    name: '',
                    templatePath: '',
                    domains: [],
                    properties: {},
                    params: {}
                };
            };


            /** Edits a report **/
            $scope.editReport = function (report) {
                $scope.editMode = 'edit';
                $scope.report = angular.copy(report);
            };


            /** Displays the error */
            $scope.displayError = function () {
                growl.error("Error saving report", { ttl: 5000 });
            };


            /** Saves the current report being edited */
            $scope.saveReport = function () {
                if ($scope.report && $scope.editMode == 'add') {
                    AdminReportService
                        .createReport($scope.report)
                        .success($scope.loadReports)
                        .error($scope.displayError);
                } else if ($scope.report && $scope.editMode == 'edit') {
                    AdminReportService
                        .updateReport($scope.report)
                        .success($scope.loadReports)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given report */
            $scope.deleteReports = function (report) {
                DialogService.showConfirmDialog(
                    "Delete report?", "Delete report ID '" + report.reportId + "'?")
                    .then(function() {
                        AdminReportService
                            .deleteReport(report)
                            .success($scope.loadReports)
                            .error($scope.displayError);
                    });
            };
        }]);