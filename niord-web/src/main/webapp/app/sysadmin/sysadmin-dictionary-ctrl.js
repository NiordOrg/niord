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
     * DictionariesAdminCtrl
     * ********************************************************************************
     * Dictionaries Admin Controller
     * Controller for the Dictionaries settings page
     */
    .controller('DictionariesAdminCtrl', ['$scope', '$rootScope', 'growl', 'LangService',
                'DialogService', 'AdminDictionariesService', 'UploadFileService',
        function ($scope, $rootScope, growl, LangService,
                  DialogService, AdminDictionariesService, UploadFileService) {
            'use strict';

            $scope.search = '';
            $scope.dictionaryNames = [];
            $scope.currentDictionaryName = 'web';
            $scope.entries = [];
            $scope.entry = undefined; // The entry being edited
            $scope.editMode = 'add';


            /** Loads the dictionary names from the back-end */
            $scope.loadDictionaryNames = function() {
                AdminDictionariesService
                    .getDictionaryNames()
                    .success(function (names) {
                        $scope.dictionaryNames = names;
                        $scope.loadDictionaryEntries();
                    });
            };


            /** Loads the dictionary entries from the back-end */
            $scope.loadDictionaryEntries = function(name) {
                $scope.entry = undefined;
                if (name && $.inArray(name, $scope.dictionaryNames) > -1) {
                    $scope.currentDictionaryName = name;
                }
                AdminDictionariesService
                    .getDictionaryEntries($scope.currentDictionaryName)
                    .success(function (dictionary) {
                        $scope.entries = dictionary;
                    });
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving entry", { ttl: 5000 });
            };


            // Used to ensure that description entities have a "value" field
            function ensureValueField(desc) {
                desc.value = '';
            }


            /** Adds an entry **/
            $scope.addEntry = function () {
                $scope.editMode = 'add';
                $scope.entry = {
                    key: '',
                    descs: []
                };
                LangService.checkDescs($scope.entry, ensureValueField);
            };


            /** Edits an entry **/
            $scope.editEntry = function (entry) {
                $scope.editMode = 'edit';
                $scope.entry = angular.copy(entry);
                LangService.checkDescs($scope.entry, ensureValueField);
            };


            /** Copies an entry **/
            $scope.copyEntry = function (entry) {
                $scope.editMode = 'add';
                $scope.entry = angular.copy(entry);
                LangService.checkDescs($scope.entry, ensureValueField);
            };


            /** Saves the current entry being edited */
            $scope.saveEntry = function () {
                if ($scope.entry && $scope.editMode === 'edit') {
                    AdminDictionariesService
                        .updateEntry($scope.currentDictionaryName, $scope.entry)
                        .success($scope.loadDictionaryEntries)
                        .error($scope.displayError);
                } else if ($scope.entry && $scope.editMode === 'add') {
                    AdminDictionariesService
                        .addEntry($scope.currentDictionaryName, $scope.entry)
                        .success($scope.loadDictionaryEntries)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given entry */
            $scope.deleteEntry = function (entry) {
                DialogService.showConfirmDialog(
                    "Delete entry?", "Delete entry '" + entry.key + "'?")
                    .then(function() {
                        AdminDictionariesService
                            .deleteEntry($scope.currentDictionaryName, entry)
                            .success($scope.loadDictionaryEntries)
                            .error($scope.displayError);
                    });
            };


            /** Opens the upload-dictionaries dialog **/
            $scope.uploadDictionariesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Dictionary JSON File',
                    '/rest/dictionaries/upload-dictionaries',
                    'json');
            };


            /** Reloads dictionaries from resource bundles **/
            $scope.reloadDictionaries = function () {
                DialogService.showConfirmDialog(
                    "Reload Dictionaries?", "Reload dictionaries from resource bundles?")
                    .then(function() {
                        AdminDictionariesService
                            .reloadDictionaries()
                            .success($scope.loadDictionaryEntries)
                            .error($scope.displayError);
                    });

            };


            $scope.updateShowExtended = function () {
                $rootScope.showExtended = $scope.showExtended;
            }
        }]);

