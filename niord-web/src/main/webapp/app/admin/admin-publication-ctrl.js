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
                 '$scope', '$rootScope', '$window', '$timeout', 'growl', 'AdminPublicationService', 'DialogService', 'LangService', 'UploadFileService',
        function ($scope, $rootScope, $window, $timeout, growl, AdminPublicationService, DialogService, LangService, UploadFileService) {
            'use strict';

            $scope.mainType = 'PUBLICATION';
            $scope.publications = [];
            $scope.publication = undefined; // The publication being edited
            $scope.publicationCategories = [];
            $scope.publicationFileUploadUrl = '';
            $scope.editMode = 'add';
            $scope.filter = {
                mainType: 'PUBLICATION',
                title: '',
                type: '',
                category: '',
                status: '',
                maxSize: 10
            };
            $scope.totalPublicationNo = 0;
            $scope.pageData = { page: 1 };


            /** Sets the mainType of publications, either 'PUBLICATION' or 'TEMPLATE' **/
            $scope.init = function (mainType) {
                $scope.filter.mainType = mainType;
                $scope.loadPublications();
            };


            /** Loads the publications from the back-end */
            $scope.loadPublications = function() {
                $scope.publication = undefined;
                AdminPublicationService
                    .searchPublications($scope.filter, $scope.pageData.page)
                    .success(function (publications) {
                        $scope.publications = publications.data;
                        $scope.totalPublicationNo = publications.total;
                    });
            };

            // Monitor changes to search filter and search result page
            $scope.$watch("filter", function () {
                $scope.pageData.page = 1;
                $scope.loadPublications();
            }, true);
            $scope.$watch("pageData", $scope.loadPublications, true);


            // Sync up publication fields used by various editors back to the proper fields
            $scope.$watch("publication", function (pub) {
                if ($scope.publication) {
                    $scope.publication.template = pub.publication;
                    $scope.publication.messageTag = pub.tag;

                    // If the languageSpecific flag is not set, sync links across descs
                    if (!$scope.publication.languageSpecific) {
                        var descs = $scope.publication.descs;
                        for (var x = 1; x < descs.length; x++) {
                            descs[x].link = descs[0].link;
                            descs[x].fileName = descs[0].fileName;
                        }
                    }
                }
            }, true);


            // Load categories
            AdminPublicationService
                .getPublicationCategories()
                .success(function (publicationCategories) {
                    $scope.publicationCategories = publicationCategories;
                });


            /** Set the form as pristine **/
            $scope.setPristine = function () {
                $timeout(function () {
                    try { angular.element($("#publicationForm")).scope().publicationForm.$setPristine(); } catch (err) { console.error(err)}
                }, 100);
            };


            /** Set the form as dirty **/
            $scope.setDirty = function () {
                $timeout(function () {
                    try { angular.element($("#publicationForm")).scope().publicationForm.$setDirty(); } catch (err) { console.error(err)}
                }, 100);
            };


            /** Returns if the given editor field should be displayed **/
            $scope.showEditorField = function (field) {
                var pub = $scope.publication;
                var isPublication = pub.mainType == 'PUBLICATION';
                var isTemplate = pub.mainType == 'TEMPLATE';
                var hasTemplate = pub.template !== undefined;
                switch (field) {
                    case 'template':
                        return isPublication;
                    case 'category':
                        return !hasTemplate || pub.template.category === undefined;
                    case 'title':
                        return !hasTemplate || pub.template.descs[0].titleFormat === undefined;
                    case 'titleFormat':
                        return isTemplate;
                    case 'messageTagFormat':
                        return isTemplate && pub.type == 'MESSAGE_REPORT';
                    case 'messageTag':
                        return isPublication && pub.type == 'MESSAGE_REPORT' && !(hasTemplate && pub.template.messageTagFormat);
                    case 'messageTagFilter':
                        return pub.type == 'MESSAGE_REPORT' && !(hasTemplate && (pub.template.messageTagFilter || pub.template.messageTagFormat));
                    case 'periodicalType':
                        return isTemplate;
                    case 'dates':
                        return isPublication;
                    case 'type':
                        return !hasTemplate || pub.template.type === undefined;
                    case 'link':
                        return (!hasTemplate && pub.type == 'LINK') || (hasTemplate && pub.template.type == 'LINK');
                    case 'repoFileName':
                        return isTemplate && pub.type == 'MESSAGE_REPORT';
                    case 'repoFile':
                        return isPublication && (pub.type == 'REPOSITORY' || pub.type == 'MESSAGE_REPORT');
                    case 'report':
                        return !hasTemplate && pub.type == 'MESSAGE_REPORT';
                    case 'messagePublication':
                        return !hasTemplate || (pub.template.messagePublication === undefined);
                }
                return true;
            };


            /** For link-based publications, check that links are defined **/
            function checkLinkDefined(pub) {
                if (pub.mainType == 'PUBLICATION' && pub.type != 'NONE') {
                    for (var x = 0; x < pub.descs.length; x++) {
                        if (!pub.descs[x].link) {
                            return false;
                        }
                    }
                }
                return true;
            }


            /** Returns if the publiaction can change status to the given status **/
            $scope.canChangeStatus = function (status) {
                var pub = $scope.publication;
                var isSaved = pub.created;
                var isPublication = pub.mainType == 'PUBLICATION';
                var curStatus = pub.status;
                switch (status) {
                    case 'DRAFT':
                        return isSaved && isPublication && curStatus == 'RECORDING';
                    case 'RECORDING':
                        return isSaved && isPublication && curStatus == 'DRAFT' && pub.type == 'MESSAGE_REPORT' && pub.messageTag;
                    case 'ACTIVE':
                        return isSaved && curStatus != 'ACTIVE' && checkLinkDefined(pub);
                    case 'INACTIVE':
                        return isSaved && curStatus == 'ACTIVE';
                }
                return false;
            };


            /** Updates the status of the currently edited publication **/
            $scope.changeStatus = function (status) {
                if ($scope.canChangeStatus(status)) {
                    DialogService.showConfirmDialog(
                        "Update Status?", "Update status to '" + status.toLowerCase() + "'?")
                        .then(function() {
                            AdminPublicationService
                                .updatePublicationStatus($scope.publication, status)
                                .success($scope.editPublication)
                                .error($scope.displayError);
                        });
                }
            };


            /** Updates the message tag filter **/
            $scope.setMessageTagFilter = function (filterType) {
                switch (filterType) {
                    case 'published':
                        $scope.publication.messageTagFilter = "msg.status == 'PUBLISHED'";
                        break;
                    case 'nm-tp':
                        $scope.publication.messageTagFilter =
                            "msg.type == 'TEMPORARY_NOTICE' || msg.type == 'PRELIMINARY_NOTICE'";
                        break;
                    case 'published-nm-tp':
                        $scope.publication.messageTagFilter =
                            "(msg.type == 'TEMPORARY_NOTICE' || msg.type == 'PRELIMINARY_NOTICE') && msg.status == 'PUBLISHED'";
                        break;
                }
                $scope.setDirty();
            };


            /** Called when a publication file has been uploaded **/
            $scope.publicationFileUploaded = function (resultDesc) {
                var desc = LangService.descForLanguage($scope.publication, resultDesc.lang);
                angular.copy(resultDesc, desc);
                $scope.$$phase || $scope.$apply();
            };


            /** Called when a publication file upload has failed **/
            $scope.publicationFileUploadError = function (status, statusText) {
                growl.error("Error uploading file:" + statusText, { ttl: 5000 });
                $scope.$$phase || $scope.$apply();
            };


            /** Called to remove the publication file or link **/
            $scope.removePublicationFile = function (desc) {
                if (desc.link) {
                    // To preserve revision history, we only delete the linked file from the
                    // repository if it is the latest (unsaved) revision.
                    if (desc.link.indexOf($scope.publication.editRepoPath + '/' + $scope.publication.revision + '/') != -1) {
                        AdminPublicationService.deletePublicationFile(desc.link);
                    }
                    delete desc.link;
                    $scope.setDirty();
                }
            };


            /** Utility function for concatenating a key-value request parameter to the parameter string **/
            function concatParam(param, k, v) {
                param = param || '';
                if (k && v) {
                    param += (param.length > 0) ? '&' : '';
                    param += encodeURIComponent(k) + '=' + encodeURIComponent(v);
                }
                return param;
            }


            /** Generates a report file for the current publication **/
            $scope.generateReport = function (desc, preview) {
                var pub = $scope.publication;
                var printSettings = pub.template ? pub.template.printSettings : pub.printSettings;

                // Check that required params are defined
                if (!pub.messageTag) {
                    growl.error("Missing message tag", { ttl: 5000 });
                    return;
                }
                if (!pub.printSettings.report) {
                    growl.error("Missing Report", { ttl: 5000 });
                    return;
                }

                var printParam = 'tag=' + encodeURIComponent(pub.messageTag.tagId);
                angular.forEach(printSettings, function (v, k) {
                    printParam = concatParam(printParam, k, v);
                });
                angular.forEach(pub.reportParams, function (v, k) {
                    printParam = concatParam(printParam, 'param:' + k, v);
                });
                printParam = concatParam(printParam, 'lang', desc.lang);

                if (desc.fileName) {
                    printParam = concatParam(printParam, 'fileName', desc.fileName);
                }

                if (preview) {
                    AdminPublicationService
                        .publicationTicket()
                        .success(function (ticket) {
                            printParam = concatParam(printParam, 'ticket', ticket);
                            $window.location = '/rest/message-reports/report.pdf?' + printParam;
                        });
                } else {
                    var repoPath = $scope.publication.editRepoPath + '/' + $scope.publication.revision;
                    AdminPublicationService
                        .generatePublicationReport(desc, repoPath, printParam)
                        .success(function (resultDesc) {
                            angular.copy(resultDesc, desc);
                            $scope.setDirty();
                        })
                }
            };


            // Used to ensure that description entities have a "title" field
            function ensureTitleField(desc) {
                desc.title = '';
            }


            /** Adds a new publication **/
            $scope.addPublication = function () {
                AdminPublicationService.newPublicationTemplate($scope.mainType)
                    .success(function (pub) {
                        $scope.editMode = 'add';
                        $scope.publication = pub;
                        LangService.checkDescs($scope.publication, ensureTitleField);
                        $scope.setDirty();
                    });
            };


            /** Edits a publication **/
            $scope.editPublication = function (publication) {
                AdminPublicationService.getPublicationDetails(publication)
                    .success(function (pub) {
                        $scope.editMode = 'edit';
                        $scope.publication = pub;
                        LangService.sortDescs($scope.publication);

                        // Some field editors rely on hardcoded property names:
                        $scope.publication.publication = pub.template;
                        $scope.publication.tag = pub.messageTag;

                        $scope.publicationFileUploadUrl = '/rest/publications/upload-publication-file/'
                            + encodeURIComponent($scope.publication.editRepoPath  + '/' + $scope.publication.revision)
                        $scope.setPristine();
                    })
                    .error($scope.displayError);
            };


            /** Copies a publication **/
            $scope.copyPublication = function (publication, nextIssue) {
                AdminPublicationService.copyPublicationTemplate(publication, nextIssue)
                    .success(function (pub) {
                        $scope.editMode = 'add';
                        $scope.publication = pub;
                        LangService.sortDescs($scope.publication);

                        // Some field editors rely on hardcoded property names:
                        $scope.publication.publication = pub.template;
                        $scope.publication.tag = pub.messageTag;

                        $scope.setDirty();
                    })
                    .error($scope.displayError);
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving publication", { ttl: 5000 });
            };


            /** Saves the current publication being edited */
            $scope.savePublication = function () {
                if ($scope.publication.messagePublication == '') {
                    delete $scope.publication.messagePublication;
                }
                if ($scope.publication.periodicalType == '') {
                    delete $scope.publication.periodicalType;
                }
                if ($scope.publication && $scope.editMode == 'add') {
                    AdminPublicationService
                        .createPublication($scope.publication)
                        .success($scope.editPublication)
                        .error($scope.displayError);
                } else if ($scope.publication && $scope.editMode == 'edit') {
                    AdminPublicationService
                        .updatePublication($scope.publication)
                        .success($scope.editPublication)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given publication */
            $scope.deletePublication = function (publication) {
                DialogService.showConfirmDialog(
                    "Delete Publication?", "Delete publication '" + publication.descs[0].title + "'?")
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
                    .publicationTicket('admin')
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
