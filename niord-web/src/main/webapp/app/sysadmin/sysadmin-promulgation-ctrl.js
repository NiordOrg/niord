/*
 * Copyright 2017 Danish Maritime Authority.
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
     * PromulgationAdminCtrl
     * ********************************************************************************
     * Promulgation Admin Controller
     * Controller for the Admin Promulgation page
     */
    .controller('PromulgationAdminCtrl', ['$scope', '$rootScope', '$uibModal', 'growl',
                'AdminPromulgationService', 'DialogService', 'UploadFileService',
        function ($scope, $rootScope, $uibModal, growl,
                  AdminPromulgationService, DialogService, UploadFileService) {
            'use strict';

            $scope.promulgationServices = [];
            $scope.promulgationTypes = [];
            $scope.promulgationType = undefined;  // The promulgation being edited
            $scope.editMode = 'add';
            $scope.languages = $rootScope.modelLanguages;

            $scope.messageTypes = {
                NW: { LOCAL_WARNING: false, COASTAL_WARNING: false, SUBAREA_WARNING: false, NAVAREA_WARNING: false },
                NM: { TEMPORARY_NOTICE: false, PRELIMINARY_NOTICE: false, PERMANENT_NOTICE: false, MISCELLANEOUS_NOTICE: false }
            };


            // Load all promulgation services
            AdminPromulgationService
                .getPromulgationServices()
                .success(function (promulgationServices) {
                    $scope.promulgationServices = promulgationServices;
                });


            /** Loads the promulgation from the back-end */
            $scope.loadPromulgationTypes = function() {
                $scope.promulgationType = undefined;
                AdminPromulgationService
                    .getPromulgationTypes()
                    .success(function (promulgationTypes) {
                        $scope.promulgationTypes = promulgationTypes;
                    });
            };


            /** Returns if the current domain selection includes message series associated with the given main type **/
            $scope.supportsMainType = function (mainType) {
                var type = $scope.promulgationType;
                var supported = type.domains === undefined || type.domains.length === 0;
                angular.forEach(type.domains, function (domain) {
                    supported |= domain.messageSeries !== undefined && $.grep(domain.messageSeries, function (ms) {
                            return ms.mainType === mainType;
                        }).length > 0;
                });
                return supported;
            };


            /** Called when the domain selection has changed **/
            $scope.domainsUpdated = function () {
                var type = $scope.promulgationType;
                if (type !== undefined) {
                    var supportsNw = $scope.supportsMainType('NW');
                    var supportsNm = $scope.supportsMainType('NM');
                    Object.keys($scope.messageTypes['NW']).forEach(function (key) {
                        $scope.messageTypes['NW'][key] = supportsNw && type.messageTypes && ($.inArray(key, type.messageTypes) !== -1);
                    });
                    Object.keys($scope.messageTypes['NM']).forEach(function (key) {
                        $scope.messageTypes['NM'][key] = supportsNm && type.messageTypes && ($.inArray(key, type.messageTypes) !== -1);
                    });
                }
            };


            /** Adds a new promulgation type **/
            $scope.addPromulgationType = function (serviceId) {
                $scope.editMode = 'add';
                $scope.promulgationType = {
                    serviceId: serviceId,
                    typeId: '',
                    name: '',
                    priority: 0,
                    active: true,
                    promulgateByDefault: false,
                    language: undefined,
                    domains: []
                };
                $scope.domainsUpdated();
            };


            /** Edits a promulgation type **/
            $scope.editPromulgationType = function (promulgationType) {
                $scope.editMode = 'edit';
                $scope.promulgationType = angular.copy(promulgationType);
                $scope.domainsUpdated();
            };


            /** Deletes a promulgation type **/
            $scope.deletePromulgationType = function (promulgationType) {
                DialogService.showConfirmDialog(
                    "Delete Template?", "Delete promulgation type '" + promulgationType.typeId + "'?")
                    .then(function() {
                        AdminPromulgationService
                            .deletePromulgationType(promulgationType)
                            .success($scope.loadPromulgationTypes)
                            .error($scope.displayError);
                    });
            };


            /** Displays the error */
            $scope.displayError = function () {
                growl.error("Error saving promulgation type", { ttl: 5000 });
            };


            /** Saves the current promulgation type being edited */
            $scope.savePromulgationType = function () {
                $scope.promulgationType.messageTypes = $scope.promulgationType.messageTypes || [];
                $scope.promulgationType.messageTypes.length = 0;
                Object.keys($scope.messageTypes['NW']).forEach(function (key) {
                    if ($scope.messageTypes['NW'][key]) {
                        $scope.promulgationType.messageTypes.push(key);
                    }
                });
                Object.keys($scope.messageTypes['NM']).forEach(function (key) {
                    if ($scope.messageTypes['NM'][key]) {
                        $scope.promulgationType.messageTypes.push(key);
                    }
                });

                if ($scope.promulgationType && $scope.editMode === 'edit') {
                    AdminPromulgationService
                        .updatePromulgationType($scope.promulgationType)
                        .success($scope.loadPromulgationTypes)
                        .error($scope.displayError);
                } else if ($scope.promulgationType && $scope.editMode === 'add') {
                    AdminPromulgationService
                        .createPromulgationType($scope.promulgationType)
                        .success($scope.loadPromulgationTypes)
                        .error($scope.displayError);
                }
            };


            /** Utility function - opens a dialog based on the given params **/
            $scope.openDialog = function (params) {
                $scope.$apply(function() {
                    $uibModal.open(params);
                });
            };


            /** Generate an export file */
            $scope.exportPromulgationTypes = function () {
                AdminPromulgationService
                    .exportTicket('sysadmin')
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/promulgations/promulgation-types/all?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Opens the upload promulgtion types dialog **/
            $scope.uploadPromulgationTypesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Promulgation Types',
                    '/rest/promulgations/upload-promulgation-types',
                    'json');
            };

        }]);