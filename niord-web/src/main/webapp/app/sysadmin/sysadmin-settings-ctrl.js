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
     * SettingsAdminCtrl
     * ********************************************************************************
     * Settings Admin Controller
     * Controller for the Admin settings page
     */
    .controller('SettingsAdminCtrl', ['$scope', 'growl', 'AdminSettingsService', 'UploadFileService',
        function ($scope, growl, AdminSettingsService, UploadFileService) {
            'use strict';

            $scope.search = '';
            $scope.settings = [];
            $scope.setting = undefined; // The setting being edited


            /** Loads the settings from the back-end */
            $scope.loadSettings = function() {
                $scope.setting = undefined;
                AdminSettingsService
                    .getEditableSettings()
                    .success(function (settings) {
                        $scope.settings = settings;
                    });
            };


            /** Edits a setting **/
            $scope.editSetting = function (setting) {
                $scope.setting = angular.copy(setting);
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving setting", { ttl: 5000 });
            };


            /** Saves the current setting being edited */
            $scope.saveSetting = function () {
                if ($scope.setting) {
                    AdminSettingsService
                        .updateSetting($scope.setting)
                        .success($scope.loadSettings)
                        .error($scope.displayError);
                }
            };

            /** Download the instance data file */
            $scope.exportSettings = function () {
                AdminSettingsService
                    .getSettingsExportTicket()
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/settings/editable-settings?ticket=' + ticket;
                        link.click();
                    });
            };

            /** Opens the upload-domains dialog **/
            $scope.uploadSettingsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Settings File',
                    '/rest/settings/upload-settings',
                    'json');
            };

        }]);
