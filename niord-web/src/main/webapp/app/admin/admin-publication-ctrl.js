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
     * Controller for the Admin Publications -> Publications and Templates pages
     */
    .controller('PublicationsAdminCtrl', [
                 '$scope', '$rootScope', 'growl', 'AdminPublicationService', 'DialogService', 'LangService', 'UploadFileService',
        function ($scope, $rootScope, growl, AdminPublicationService, DialogService, LangService, UploadFileService) {
            'use strict';

            $scope.publications = [];
            $scope.publication = undefined; // The publication being edited
            $scope.publicationCategories = [];
            $scope.editMode = 'add';
            $scope.filter = {
                title: '',
                type: '',
                category: ''
            };


            /** Loads the publications from the back-end */
            $scope.loadPublications = function() {
                $scope.publication = undefined;
                AdminPublicationService
                    .searchPublications($scope.filter.title, $scope.filter.type, $scope.filter.category)
                    .success(function (publications) {
                        $scope.publications = publications;
                    });
            };
            $scope.$watch("filter", $scope.loadPublications, true);


            // Load categories
            AdminPublicationService
                .getPublicationCategories()
                .success(function (publicationCategories) {
                    $scope.publicationCategories = publicationCategories;
                });


            // Used to ensure that description entities have a "title" field
            function ensureTitleField(desc) {
                desc.title = '';
            }


            /** Adds a new publication **/
            $scope.addPublication = function () {
                $scope.editMode = 'add';
                $scope.publication = {
                    publicationId: undefined,
                    fileType: 'LINK',
                    messagePublication: 'NONE',
                    languageSpecific: true,
                    descs: []
                };
                LangService.checkDescs($scope.publication, ensureTitleField);
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
                        LangService.checkDescs($scope.publication, ensureTitleField);
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


            /** Generate an export file */
            $scope.exportPublications = function () {
                AdminPublicationService
                    .publicationExportTicket()
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/publications/export?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Opens the upload-publications dialog **/
            $scope.uploadPublicationsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Publications File',
                    '/rest/publications/upload-publications',
                    'json');
            };

        }])


    /**
     * ********************************************************************************
     * PublicationCategoriesAdminCtrl
     * ********************************************************************************
     * Publication Categories Admin Controller
     * Controller for the Admin Publications -> Categories page
     */
    .controller('PublicationCategoriesAdminCtrl', [
        '$scope', '$rootScope', 'growl', 'AdminPublicationService', 'DialogService', 'LangService', 'UploadFileService',
        function ($scope, $rootScope, growl, AdminPublicationService, DialogService, LangService, UploadFileService) {
            'use strict';

            $scope.publicationCategories = [];
            $scope.filteredPublicationCategories = [];
            $scope.publicationCategory = undefined; // The publicationCategory being edited
            $scope.editMode = 'add';
            $scope.filter = {
                search: ''
            };


            /** Filters messages by name **/
            $scope.updateFilteredPublicationCategories = function () {
                $scope.filteredPublicationCategories = $.grep($scope.publicationCategories, function (pubCat) {
                    return pubCat.descs[0].name.toLowerCase().indexOf($scope.filter.search.toLowerCase()) !== -1;
                });
            };
            $scope.$watch("filter.search", $scope.updateFilteredPublicationCategories, true);


            /** Loads the publication categories from the back-end */
            $scope.loadPublicationCategories = function() {
                $scope.publicationCategory = undefined;
                AdminPublicationService
                    .getPublicationCategories()
                    .success(function (publicationCategories) {
                        $scope.publicationCategories = publicationCategories;
                        $scope.updateFilteredPublicationCategories();
                    });
            };
            $scope.loadPublicationCategories();


            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
            }


            /** Adds a new publication categories **/
            $scope.addPublicationCategory = function () {
                $scope.editMode = 'add';
                $scope.publicationCategory = {
                    priority: 100,
                    publish: false,
                    descs: []
                };
                LangService.checkDescs($scope.publicationCategory, ensureNameField);
            };


            /** Edits a publication category **/
            $scope.editPublicationCategory = function (publicationCategory) {
                AdminPublicationService.getPublicationCategoryDetails(publicationCategory)
                    .success(function (pub) {
                        $scope.editMode = 'edit';
                        $scope.publicationCategory = pub;
                        LangService.sortDescs($scope.publicationCategory)
                    })
                    .error($scope.displayError);
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving publication category", { ttl: 5000 });
            };


            /** Saves the current publication category being edited */
            $scope.savePublicationCategory = function () {

                if ($scope.publicationCategory && $scope.editMode == 'add') {
                    AdminPublicationService
                        .createPublicationCategory($scope.publicationCategory)
                        .success($scope.loadPublicationCategories)
                        .error($scope.displayError);
                } else if ($scope.publicationCategory && $scope.editMode == 'edit') {
                    AdminPublicationService
                        .updatePublicationCategory($scope.publicationCategory)
                        .success($scope.loadPublicationCategories)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given publication category */
            $scope.deletePublicationCategory = function (publicationCategory) {
                DialogService.showConfirmDialog(
                    "Delete Publication category?", "Delete publication category '" + publicationCategory.descs[0].name + "'?")
                    .then(function() {
                        AdminPublicationService
                            .deletePublicationCategory(publicationCategory)
                            .success($scope.loadPublicationCategories)
                            .error($scope.displayError);
                    });
            };


            /** Opens the upload-publication-categories dialog **/
            $scope.uploadPublicationCategoriesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Publications Categories File',
                    '/rest/publication-categories/upload-publication-categories',
                    'json');
            };

        }]);
