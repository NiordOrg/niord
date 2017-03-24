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
     * CategoryAdminCtrl
     * ********************************************************************************
     * Category Admin Controller
     * Controller for the Admin Categories page
     */
    .controller('CategoryAdminCtrl', ['$scope', '$rootScope', '$timeout', 'growl',
                'LangService', 'AdminCategoryService', 'DialogService', 'UploadFileService',
        function ($scope, $rootScope, $timeout, growl,
                  LangService, AdminCategoryService, DialogService, UploadFileService) {
            'use strict';

            $scope.hasRole = $rootScope.hasRole;
            $scope.categories = [];
            $scope.category = undefined;
            $scope.editCategory = undefined;
            $scope.action = "edit";
            $scope.categoryFilter = '';

            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
            }


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Category operation failed", { ttl: 5000 });
            };


            /** If the category form is visible set it to be pristine */
            $scope.setPristine = function () {
                if ($scope.categoryForm) {
                    $scope.categoryForm.$setPristine();
                }
            };


            /** Set the form as dirty **/
            $scope.setDirty = function () {
                if ($scope.categoryForm) {
                    $scope.categoryForm.$setDirty();
                }
            };


            /** Load the categories */
            $scope.loadCategories = function() {
                AdminCategoryService
                    .getCategories()
                    .success(function (categories) {
                        $scope.categories = categories;
                        $scope.category = undefined;
                        $scope.editCategory = undefined;
                        $scope.setPristine();
                    })
                    .error ($scope.displayError);
            };


            /** Creates a new category */
            $scope.newCategory = function() {
                $scope.action = "add";
                $scope.editCategory = LangService.checkDescs({
                    active: true,
                    type: 'CATEGORY',
                    domains: [],
                    stdTemplateFields: [],
                    templateParams: [],
                    scriptResourcePaths: [ '' ],
                    messageId: undefined
                    }, ensureNameField);
                if ($scope.category) {
                    $scope.editCategory.parent = { id: $scope.category.id };
                }
                $scope.setPristine();
            };


            /** Called when an categories is selected */
            $scope.selectCategory = function (category) {
                AdminCategoryService
                    .getCategory(category)
                    .success(function (data) {
                        $scope.action = "edit";
                        $scope.category = LangService.checkDescs(data, ensureNameField);
                        if ($scope.category.templateParams) {
                            angular.forEach($scope.category.templateParams, function (param) {
                                LangService.checkDescs(param, ensureNameField);
                            })
                        }
                        $scope.editCategory = angular.copy($scope.category);
                        if ($scope.editCategory.scriptResourcePaths.length == 0) {
                            $scope.editCategory.scriptResourcePaths.push('');
                        }
                        $scope.setPristine();
                        $scope.$$phase || $scope.$apply();
                    })
                    .error($scope.displayError);
            };



            /** Called when an category has been dragged to a new parent category */
            $scope.moveCategory = function (category, parent) {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Move Category?", "Move " + category.descs[0].name + " to " + ((parent) ? parent.descs[0].name : "the root") + "?")
                    .then(function() {
                        AdminCategoryService
                            .moveCategory(category.id, (parent) ? parent.id : undefined)
                            .success($scope.loadCategories)
                            .error($scope.displayError);
                    });
            };


            /** Called when the sibling category sort order has changed for the currently selected category */
            $scope.changeSiblingSortOrder = function (moveUp) {
                AdminCategoryService
                    .changeSortOrder($scope.category.id, moveUp)
                    .success($scope.loadCategories)
                    .error($scope.displayError);
            };


            /** Will query the back-end to recompute the tree sort order */
            $scope.recomputeTreeSortOrder = function () {
                AdminCategoryService
                    .recomputeTreeSortOrder()
                    .success(function () {
                        growl.info('Tree Sort Order Updated', { ttl: 3000 });
                    })
                    .error($scope.displayError);
            };


            /** Saves the current category */
            $scope.saveCategory = function () {

                if ($scope.action == 'add') {
                    AdminCategoryService
                        .createCategory($scope.editCategory)
                        .success($scope.loadCategories)
                        .error ($scope.displayError);

                } else {
                    AdminCategoryService
                        .updateCategory($scope.editCategory)
                        .success($scope.loadCategories)
                        .error ($scope.displayError);
                }
            };


            /** Deletes the current category */
            $scope.deleteCategory = function () {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Delete Category?", "Delete category " + $scope.category.descs[0].name + "?")
                    .then(function() {
                        AdminCategoryService
                            .deleteCategory($scope.editCategory)
                            .success($scope.loadCategories)
                            .error ($scope.displayError);
                    });
            };


            /** Opens the upload-categories dialog **/
            $scope.uploadCategoriesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Category JSON File',
                    '/rest/categories/upload-categories',
                    'json');
            };



            /** Tests the current template with the specified message ID */
            $scope.executeTemplate = function (template) {
                if ($scope.editCategory.messageId) {

                    AdminCategoryService
                        .executeCategoryTemplate(template, $scope.editCategory.messageId)
                        .success(function (message) {
                            AdminCategoryService.showCategoryTemplateResult(message);
                        })
                        .error(function (data, status) {
                            growl.error("Error executing template (code: " + status + ")", {ttl: 5000})
                        });
                }
            };
        }])


    /*******************************************************************
     * Displays the result of executing a template on a message
     *******************************************************************/
    .controller('TemplateCategoryResultDialogCtrl', ['$scope', '$rootScope', 'LangService', 'message',
        function ($scope, $rootScope, LangService, message) {
            'use strict';

            $scope.message = message;
            $scope.previewLang = $rootScope.language;

            /** Create a preview message, i.e. a message sorted to the currently selected language **/
            $scope.updatePreviewMessage = function () {
                $scope.previewMessage = undefined;
                if ($scope.message) {
                    $scope.previewMessage = angular.copy($scope.message);
                    LangService.sortMessageDescs($scope.previewMessage, $scope.previewLang);
                }
            };
            $scope.$watch("message", $scope.updatePreviewMessage, true);


            /** Set the preview language **/
            $scope.previewLanguage = function (lang) {
                $scope.previewLang = lang;
                $scope.updatePreviewMessage();
            };
        }]);
