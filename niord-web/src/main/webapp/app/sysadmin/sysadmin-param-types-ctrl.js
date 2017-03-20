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
     * ParamTypesAdminCtrl
     * ********************************************************************************
     * ParamTypes Admin Controller
     * Controller for the Admin Parameter Types page
     */
    .controller('ParamTypesAdminCtrl', ['$scope', 'growl',
                'LangService', 'AdminParamTypesService', 'AdminDictionariesService', 'DialogService', 'UploadFileService',
        function ($scope, growl,
                  LangService, AdminParamTypesService, AdminDictionariesService, DialogService, UploadFileService) {
            'use strict';

            $scope.paramTypes = [];
            $scope.paramType = undefined; // The paramType being edited
            $scope.editMode = 'add';
            $scope.search = '';

            // Editing of list params
            $scope.dictionaryNames = [];
            $scope.currentDictionaryName = 'template';
            $scope.dictionaryEntries = [];


            AdminDictionariesService
                .getDictionaryNames()
                .success(function (names) {
                    $scope.dictionaryNames = names;
                    $scope.loadDictionaryEntries();
                });


            /** Loads the dictionary entries from the back-end */
            $scope.loadDictionaryEntries = function(name) {
                name = name || $scope.currentDictionaryName;
                if (name && $.inArray(name, $scope.dictionaryNames) > -1) {
                    $scope.currentDictionaryName = name;
                }
                if ($scope.paramType && $scope.paramType.type == 'LIST') {
                    AdminDictionariesService
                        .getDictionaryEntries($scope.currentDictionaryName)
                        .success(function (dictionary) {
                            var idMap = {};
                            angular.forEach($scope.paramType.values, function (entry) {
                                idMap[entry.id] = true;
                            });
                            $scope.dictionaryEntries.length = 0;
                            angular.forEach(dictionary, function (entry) {
                                if (!idMap[entry.id]) {
                                    $scope.dictionaryEntries.push(entry);
                                }
                            });
                        });
                }
            };


            /** Parameter list value DnD configuration **/
            $scope.paramListValueSortableCfg = {
                group: 'paramListValue',
                handle: '.move-btn'
            };


            /** Adds the dictionary entry to the list parameter values **/
            $scope.addParamListValue = function (paramType, value) {
                $scope.dictionaryEntries.splice( $.inArray(value, $scope.dictionaryEntries), 1 );
                paramType.values.push(value);
            };


            /** Remove the dictionary entry from the list parameter values **/
            $scope.removeParamListValue = function (paramType, value) {
                paramType.values.splice( $.inArray(value, paramType.values), 1 );
                $scope.loadDictionaryEntries();
            };


            /** Remove all list parameter values **/
            $scope.clearParamListValues = function (paramType) {
                paramType.values.length = 0;
                $scope.loadDictionaryEntries();
            };


            /** Loads the paramTypes from the back-end */
            $scope.loadParamTypes = function() {
                $scope.paramType = undefined;
                AdminParamTypesService
                    .getParamTypes()
                    .success(function (paramTypes) {
                        $scope.paramTypes = paramTypes;
                    });
            };


            /** Adds a new parameter type **/
            $scope.addParamType = function (type) {
                $scope.editMode = 'add';
                $scope.paramType = {
                    type: type,
                    id: undefined,
                    name: undefined,
                    values: []
                };
                $scope.loadDictionaryEntries();
            };


            /** Copies a parameter type **/
            $scope.copyParamType = function (paramType) {
                AdminParamTypesService.getParamTypeDetails(paramType.id)
                    .success(function (type) {
                        $scope.editMode = 'add';
                        $scope.paramType = type;
                        $scope.paramType.id = undefined;
                        $scope.paramType.name = undefined;
                        $scope.loadDictionaryEntries();
                    });
            };


            /** Edits a parameter type **/
            $scope.editParamType = function (paramType) {
                if (paramType == 'STANDARD') {
                    return;
                }
                AdminParamTypesService.getParamTypeDetails(paramType.id)
                    .success(function (type) {
                        $scope.editMode = 'edit';
                        $scope.paramType = type;
                        $scope.loadDictionaryEntries();
                    });
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving parameter type", { ttl: 5000 });
            };


            /** Saves the current parameter type being edited */
            $scope.saveParamType = function () {
                if (paramType == 'STANDARD') {
                    return;
                }

                if ($scope.paramType && $scope.editMode == 'add') {
                    AdminParamTypesService
                        .createParamType($scope.paramType)
                        .success($scope.loadParamTypes)
                        .error($scope.displayError);
                } else if ($scope.paramType && $scope.editMode == 'edit') {
                    AdminParamTypesService
                        .updateParamType($scope.paramType)
                        .success($scope.loadParamTypes)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given parameter type */
            $scope.deleteParamType = function (paramType) {
                if (paramType == 'STANDARD') {
                    return;
                }

                DialogService.showConfirmDialog(
                    "Delete parameter type?", "Delete parameter type '" + paramType.name + "'?")
                    .then(function() {
                        AdminParamTypesService
                            .deleteParamType(paramType)
                            .success($scope.loadParamTypes)
                            .error($scope.displayError);
                    });
            };

            /** Opens the upload-parameter-types dialog **/
            $scope.uploadParamTypesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload ParamTypes File',
                    '/rest/templates/upload-param-types',
                    'json');
            };
        }]);
