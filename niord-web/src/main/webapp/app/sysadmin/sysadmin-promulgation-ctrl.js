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
    .controller('PromulgationAdminCtrl', ['$scope', 'growl', 'AdminPromulgationService',
        function ($scope, growl, AdminPromulgationService) {
            'use strict';

            $scope.promulgations = [];
            $scope.promulgation = undefined;  // The promulgation being edited


            /** Loads the promulgation from the back-end */
            $scope.loadPromulgations = function() {
                $scope.promulgation = undefined;
                AdminPromulgationService
                    .getPromulgations()
                    .success(function (promulgations) {
                        $scope.promulgations = promulgations;
                    });
            };


            /** Edits a promulgation **/
            $scope.editPromulgation = function (promulgation) {
                $scope.promulgation = angular.copy(promulgation);
            };


            /** Displays the error */
            $scope.displayError = function () {
                growl.error("Error saving promulgation", { ttl: 5000 });
            };


            /** Saves the current promulgation being edited */
            $scope.savePromulgation = function () {
                if ($scope.promulgation) {
                    AdminPromulgationService
                        .updatePromulgation($scope.promulgation)
                        .success($scope.loadPromulgations)
                        .error($scope.displayError);
                }
            };

        }]);