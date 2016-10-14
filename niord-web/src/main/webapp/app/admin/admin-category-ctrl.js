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
    .controller('CategoryAdminCtrl', ['$scope', 'growl', 'LangService', 'AdminCategoryService', 'DialogService', 'UploadFileService',
        function ($scope, growl, LangService, AdminCategoryService, DialogService, UploadFileService) {
            'use strict';

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
                $scope.editCategory = LangService.checkDescs({ active: true }, ensureNameField);
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
                        $scope.editCategory = angular.copy($scope.category);
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
        }]);
