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
     * AreaAdminCtrl
     * ********************************************************************************
     * Area Admin Controller
     * Controller for the Admin Areas page
     */
    .controller('AreaAdminCtrl', ['$scope', 'growl', 'LangService', 'AdminAreaService', 'DialogService', 'UploadFileService',
        function ($scope, growl, LangService, AdminAreaService, DialogService, UploadFileService) {
            'use strict';

            $scope.areas = [];
            $scope.area = undefined;
            $scope.editArea = undefined;
            $scope.defineMessageSorting = true;
            $scope.action = "edit";
            $scope.areaFilter = '';
            $scope.areaFeatureCollection = { type: 'FeatureCollection', features: [] };


            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
            }


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Area operation failed", { ttl: 5000 });
            };


            /** If the area form is visible set it to be pristine */
            $scope.setPristine = function () {
                if ($scope.areaForm) {
                    $scope.areaForm.$setPristine();
                }
            };

            /** Load the areas */
            $scope.loadAreas = function() {
                AdminAreaService
                    .getAreas()
                    .success(function (areas) {
                        $scope.areas = areas;
                        $scope.area = undefined;
                        $scope.editArea = undefined;
                        $scope.setPristine();
                    })
                    .error ($scope.displayError);
            };


            /** Creates a new area */
            $scope.newArea = function() {
                $scope.action = "add";
                $scope.editArea = LangService.checkDescs({ active: true }, ensureNameField);
                if ($scope.area) {
                    $scope.editArea.parent = { id: $scope.area.id };
                }
                $scope.areaFeatureCollection.features.length = 0;
                $scope.setPristine();
            };


            /** Called when an areas is selected */
            $scope.selectArea = function (area) {
                AdminAreaService
                    .getArea(area, true)
                    .success(function (data) {
                        $scope.action = "edit";

                        // If any of the parent areas define the messageSorting field, this are should not edit it
                        $scope.defineMessageSorting = true;
                        for (var parent = data.parent; parent && $scope.defineMessageSorting; parent = parent.parent) {
                            if (parent.messageSorting) {
                                $scope.defineMessageSorting = false;
                            }
                        }
                        delete data.parent;

                        $scope.area = LangService.checkDescs(data, ensureNameField);
                        $scope.editArea = angular.copy($scope.area);
                        $scope.areaFeatureCollection.features.length = 0;
                        if ($scope.editArea.geometry) {
                            var feature = {type: 'Feature', geometry: $scope.editArea.geometry, properties: {}};
                            $scope.areaFeatureCollection.features.push(feature);
                        }
                        $scope.setPristine();
                        $scope.$$phase || $scope.$apply();
                    })
                    .error($scope.displayError);
            };


            /** Called when an area has been dragged to a new parent area */
            $scope.moveArea = function (area, parent) {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Move Area?", "Move " + area.descs[0].name + " to " + ((parent) ? parent.descs[0].name : "the root") + "?")
                    .then(function() {
                        AdminAreaService
                            .moveArea(area.id, (parent) ? parent.id : undefined)
                            .success($scope.loadAreas)
                            .error($scope.displayError);
                    });
            };


            /** Called when the sibling area sort order has changed for the currently selected area */
            $scope.changeSiblingSortOrder = function (moveUp) {
                AdminAreaService
                    .changeSortOrder($scope.area.id, moveUp)
                    .success($scope.loadAreas)
                    .error($scope.displayError);
            };


            /** Will query the back-end to recompute the tree sort order */
            $scope.recomputeTreeSortOrder = function () {
                AdminAreaService
                    .recomputeTreeSortOrder()
                    .success(function () {
                        growl.info('Tree Sort Order Updated', { ttl: 3000 });
                    })
                    .error($scope.displayError);
            };


            /** Will query the back-end to recompute the published messages area-sort order */
            $scope.recomputePublishedMessagesSortOrder = function () {
                AdminAreaService
                    .recomputePublishedMessagesSortOrder()
                    .success(function () {
                        growl.info('Tree Sort Order Updated', { ttl: 3000 });
                    })
                    .error($scope.displayError);
            };


            /** Saves the current area */
            $scope.saveArea = function () {
                // Update the area geometry
                delete $scope.editArea.geometry;
                if ($scope.areaFeatureCollection.features.length > 0 &&
                    $scope.areaFeatureCollection.features[0].geometry) {
                    $scope.editArea.geometry = $scope.areaFeatureCollection.features[0].geometry;
                }
                // Handle blank type2
                if ($scope.editArea.type == '') {
                    delete $scope.editArea.type;
                }
                if ($scope.editArea.messageSorting == '') {
                    delete $scope.editArea.messageSorting;
                }

                if ($scope.action == 'add') {
                    AdminAreaService
                        .createArea($scope.editArea)
                        .success($scope.loadAreas)
                        .error ($scope.displayError);

                } else {
                    AdminAreaService
                        .updateArea($scope.editArea)
                        .success($scope.loadAreas)
                        .error ($scope.displayError);
                }
            };


            /** Called when the area geometry editor is saved */
            $scope.geometrySaved = function () {
                if ($scope.areaForm) {
                    $scope.areaForm.$setDirty();
                }
            };


            /** Deletes the current area */
            $scope.deleteArea = function () {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Delete Area?", "Delete area " + $scope.area.descs[0].name + "?")
                    .then(function() {
                        AdminAreaService
                            .deleteArea($scope.editArea)
                            .success($scope.loadAreas)
                            .error ($scope.displayError);
                    });
            };


            /** Opens the upload-areas dialog **/
            $scope.uploadAreasDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Area JSON File',
                    '/rest/areas/upload-areas',
                    'json');
            };
        }]);
