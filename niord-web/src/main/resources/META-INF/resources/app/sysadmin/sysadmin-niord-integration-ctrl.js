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
     * NiordIntegrationAdminCtrl
     * ********************************************************************************
     * Niord Integration Admin Controller
     * Controller for the Admin Niord integration page
     */
    .controller('NiordIntegrationAdminCtrl', ['$scope', '$rootScope', 'growl',
                'AdminNiordIntegrationService', 'AdminMessageSeriesService', 'DialogService',
        function ($scope, $rootScope, growl,
                 AdminNiordIntegrationService, AdminMessageSeriesService, DialogService) {
            'use strict';

            $scope.integrations = [];
            $scope.integration = undefined; // The integration being edited
            $scope.editMode = 'add';


            // Update the list of message series
            $scope.messageSeries = [];
            AdminMessageSeriesService.getMessageSeries()
                .success(function (messageSeries) {
                    $scope.messageSeries = messageSeries;
                });


            /** Loads the integrations from the back-end */
            $scope.loadNiordIntegrations = function() {
                $scope.integration = undefined;
                AdminNiordIntegrationService
                    .getNiordIntegrations()
                    .success(function (integrations) {
                        $scope.integrations = integrations;
                    });
            };


            /** Adds a new Niord integration **/
            $scope.addNiordIntegration = function () {
                $scope.editMode = 'add';
                $scope.integration = {
                    id: undefined,
                    url: '',
                    active: true,
                    assignNewUids: true,
                    createBaseData: false,
                    messageSeriesMappings: []
                };
                $scope.addMessageSeriesMapping($scope.integration);
            };


            /** Edits a Niord integration **/
            $scope.editNiordIntegration = function (integration) {
                $scope.editMode = 'edit';
                $scope.integration = angular.copy(integration);
                if ($scope.integration.messageSeriesMappings.length === 0) {
                    $scope.addMessageSeriesMapping($scope.integration);
                }
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving Niord integration", { ttl: 5000 });
            };


            /** Adds a message series mapping to the Niord integration */
            $scope.addMessageSeriesMapping = function (integration) {
                integration.messageSeriesMappings.push({
                    sourceSeriesId: '',
                    targetSeriesId: undefined
                })
            };


            /** Removes a message series mapping from the Niord integration */
            $scope.deleteMessageSeriesMapping = function (integration, mi) {
                integration.messageSeriesMappings.splice( $.inArray(mi, integration.messageSeriesMappings), 1 );
                if (integration.messageSeriesMappings.length === 0) {
                    $scope.addMessageSeriesMapping(integration);
                }
            };


            /** Saves the current Niord integration being edited */
            $scope.saveNiordIntegration = function () {

                if ($scope.editMode === 'add') {
                    AdminNiordIntegrationService
                        .createNiordIntegration($scope.integration)
                        .success($scope.loadNiordIntegrations)
                        .error($scope.displayError);
                } else if ($scope.editMode === 'edit') {
                    AdminNiordIntegrationService
                        .updateNiordIntegration($scope.integration)
                        .success($scope.loadNiordIntegrations)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given Niord integration */
            $scope.deleteNiordIntegration = function (integration) {
                DialogService.showConfirmDialog(
                    "Delete Niord integration?", "Delete Niord integration ID '" + integration.id + "'?")
                    .then(function() {
                        AdminNiordIntegrationService
                            .deleteNiordIntegration(integration)
                            .success($scope.loadNiordIntegrations)
                            .error($scope.displayError);
                    });
            };


            /** Executes the Niord integration **/
            $scope.executeNiordIntegration = function (integration) {
                AdminNiordIntegrationService.executeNiordIntegration(integration);
            }

        }]);

