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
     * SourcesAdminCtrl
     * ********************************************************************************
     * Sources Admin Controller
     * Controller for the Admin Sources page
     */
    .controller('SourcesAdminCtrl', [
                 '$scope', 'growl', 'AdminSourceService', 'DialogService', 'LangService', 'UploadFileService',
        function ($scope, growl, AdminSourceService, DialogService, LangService, UploadFileService) {
            'use strict';

            $scope.allSources = [];
            $scope.source = undefined; // The source being edited
            $scope.editMode = 'add';

            // Pagination
            $scope.sources = [];
            $scope.pageSize = 20;
            $scope.currentPage = 1;
            $scope.sourceNo = 0;
            $scope.search = '';


            /** Loads the sources from the back-end */
            $scope.loadSources = function() {
                $scope.source = undefined;
                AdminSourceService
                    .getSources()
                    .success(function (sources) {
                        $scope.allSources = sources;
                        $scope.pageChanged();
                    });
            };


            /** Returns if the string matches the given source property */
            function match(sourceProperty, str) {
                var txt = (sourceProperty) ? "" + sourceProperty : "";
                return txt.toLowerCase().indexOf(str.toLowerCase()) >= 0;
            }


            /** Called whenever source pagination changes */
            $scope.pageChanged = function() {
                var search = $scope.search.toLowerCase();
                var filteredSources = $scope.allSources.filter(function (source) {
                    return source.descs !== null &&
                        match(source.descs[0].name, search) ||
                        match(source.descs[0].abbreviation, search);
                });
                $scope.sourceNo = filteredSources.length;
                $scope.sources = filteredSources.slice(
                    $scope.pageSize * ($scope.currentPage - 1),
                    Math.min($scope.sourceNo, $scope.pageSize * $scope.currentPage));
            };
            $scope.$watch("search", $scope.pageChanged, true);


            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
                desc.abbreviation = '';
            }


            /** Adds a new source **/
            $scope.addSource = function () {
                $scope.editMode = 'add';
                $scope.source = {
                    id: undefined,
                    active: true,
                    descs: []
                };
                LangService.checkDescs($scope.source, ensureNameField);
            };


            /** Edits a source **/
            $scope.editSource = function (source) {
                AdminSourceService.getSourceDetails(source)
                    .success(function (pub) {
                        $scope.editMode = 'edit';
                        $scope.source = pub;
                        LangService.sortDescs($scope.source)
                    })
                    .error($scope.displayError);
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving source", { ttl: 5000 });
            };


            /** Saves the current source being edited */
            $scope.saveSource = function () {

                if ($scope.source && $scope.editMode == 'add') {
                    AdminSourceService
                        .createSource($scope.source)
                        .success($scope.loadSources)
                        .error($scope.displayError);
                } else if ($scope.source && $scope.editMode == 'edit') {
                    AdminSourceService
                        .updateSource($scope.source)
                        .success($scope.loadSources)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given source */
            $scope.deleteSource = function (source) {
                DialogService.showConfirmDialog(
                    "Delete Source?", "Delete source '" + source.descs[0].name + "'?")
                    .then(function() {
                        AdminSourceService
                            .deleteSource(source)
                            .success($scope.loadSources)
                            .error($scope.displayError);
                    });
            };



            /** Opens the upload-sources dialog **/
            $scope.uploadSourcesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Sources File',
                    '/rest/sources/upload-sources',
                    'json');
            };

        }]);
