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
    .controller('ReportsAdminCtrl', ['$scope', '$rootScope', '$window', '$timeout', '$uibModal', 'growl',
                'AdminReportService', 'AdminScriptResourceService', 'MessageService', 'DialogService', 'UploadFileService',
        function ($scope, $rootScope, $window, $timeout, $uibModal, growl,
                  AdminReportService, AdminScriptResourceService, MessageService, DialogService, UploadFileService) {
            'use strict';

            $scope.reports = [];
            $scope.report = undefined;  // The report being edited
            $scope.editMode = 'add';
            $scope.search = '';

            $scope.jsonEditorOptions = {
                useWrapMode : true,
                showGutter: false,
                mode: 'json',
                onLoad: function(editor) {
                    editor.$blockScrolling = 1;
                }
            };

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
                    sortOrder: 10,
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


            /** Set the form as dirty **/
            $scope.setDirty = function () {
                $timeout(function () {
                    try { angular.element($("#reportForm")).scope().reportForm.$setDirty(); } catch (err) { }
                }, 100);
            };


            /** Saves the current report being edited */
            $scope.saveReport = function () {
                if ($scope.report && $scope.editMode === 'add') {
                    AdminReportService
                        .createReport($scope.report)
                        .success($scope.loadReports)
                        .error($scope.displayError);
                } else if ($scope.report && $scope.editMode === 'edit') {
                    AdminReportService
                        .updateReport($scope.report)
                        .success($scope.loadReports)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given report */
            $scope.deleteReport = function (report) {
                DialogService.showConfirmDialog(
                    "Delete report?", "Delete report ID '" + report.reportId + "'?")
                    .then(function() {
                        AdminReportService
                            .deleteReport(report)
                            .success($scope.loadReports)
                            .error($scope.displayError);
                    });
            };


            /** Opens a dialog for template selection **/
            $scope.selectTemplatePath = function () {
                AdminScriptResourceService.scriptResourceDialog('FM')
                    .result.then(function (scriptResource) {
                        $scope.report.templatePath = scriptResource.path;
                        $scope.setDirty();
                })
            };


            /** Generate an export file */
            $scope.exportReports = function () {
                AdminReportService
                    .reportsTicket('sysadmin')
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/message-reports/all?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadReportsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Reports File',
                    '/rest/message-reports/upload-reports',
                    'json');
            };


            $scope.testTagData = {
                tag: undefined
            };

            /** Test the current report with the messages of the selected message tag **/
            $scope.testReport = function (report) {
                if (report && $scope.testTagData.tag) {
                    MessageService.messagePrintDialog(undefined, true, report).result
                        .then(function (printParams) {
                            printParams += '&tag=' + encodeURIComponent($scope.testTagData.tag.tagId);
                            $window.location = '/rest/message-reports/report.pdf?' + printParams;
                        });
                }
            };

        }]);