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
     * PublicationsAdminCtrl
     * ********************************************************************************
     * Publications Admin Controller
     * Controller for the Admin Publications page
     */
    .controller('PublicationsAdminCtrl', [
                 '$scope', '$rootScope', 'growl', 'AdminPublicationService', 'DialogService', 'LangService', 'UploadFileService',
        function ($scope, $rootScope, growl, AdminPublicationService, DialogService, LangService, UploadFileService) {
            'use strict';

            $scope.publications = [];
            $scope.publication = undefined; // The publication being edited
            $scope.editMode = 'add';
            $scope.search = '';


            /** Filters messages by name **/
            $scope.searchFilter = function (pub) {
                return pub.descs[0].title.toLowerCase().indexOf($scope.search.toLowerCase()) !== -1;
            };


            /** Loads the publications from the back-end */
            $scope.loadPublications = function() {
                $scope.publication = undefined;
                AdminPublicationService
                    .getPublications()
                    .success(function (publications) {
                        $scope.publications = publications;
                    });
            };


            // Used to ensure that description entities have a "title" field
            function ensureNameField(desc) {
                desc.title = '';
            }


            /** Adds a new publication **/
            $scope.addPublication = function () {
                $scope.editMode = 'add';
                $scope.publication = {
                    publicationId: undefined,
                    type: 'EXTERNAL',
                    active: true,
                    languageSpecific: true,
                    descs: []
                };
                LangService.checkDescs($scope.publication, ensureNameField);
            };


            /** Edits a publication **/
            $scope.editPublication = function (publication) {
                AdminPublicationService.getPublicationDetails(publication)
                    .success(function (pub) {
                        $scope.editMode = 'edit';
                        $scope.publication = pub;
                        LangService.sortDescs($scope.publication)
                    })
                    .error($scope.displayError);
            };


            /** Called when the languageSpecific flag has been changed **/
            $scope.languageSpecificUpdate = function () {
                if ($scope.publication) {
                    if ($scope.publication.languageSpecific) {
                        $scope.publication.descs[0].lang = $rootScope.language;
                        LangService.checkDescs($scope.publication, ensureNameField);
                    } else {
                        $scope.publication.descs.length = 1;
                        delete $scope.publication.descs[0].lang;
                    }
                }
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving publication", { ttl: 5000 });
            };


            /** Saves the current publication being edited */
            $scope.savePublication = function () {

                if ($scope.publication && $scope.editMode == 'add') {
                    AdminPublicationService
                        .createPublication($scope.publication)
                        .success($scope.loadPublications)
                        .error($scope.displayError);
                } else if ($scope.publication && $scope.editMode == 'edit') {
                    AdminPublicationService
                        .updatePublication($scope.publication)
                        .success($scope.loadPublications)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given publication */
            $scope.deletePublication = function (publication) {
                DialogService.showConfirmDialog(
                    "Delete Publication?", "Delete publication '" + publication.descs[0].name + "'?")
                    .then(function() {
                        AdminPublicationService
                            .deletePublication(publication)
                            .success($scope.loadPublications)
                            .error($scope.displayError);
                    });
            };



            /** Opens the upload-publications dialog **/
            $scope.uploadPublicationsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Publications File',
                    '/rest/publications/upload-publications',
                    'json');
            };

        }]);
