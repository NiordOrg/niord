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
     * DomainAdminCtrl
     * ********************************************************************************
     * Domains Admin Controller
     * Controller for the Admin domains page
     */
    .controller('DomainAdminCtrl', ['$scope', 'growl', 'LangService', 'AuthService', 'AdminDomainService', 'DialogService', 'UploadFileService',
        function ($scope, growl, LangService, AuthService, AdminDomainService, DialogService, UploadFileService) {
            'use strict';

            $scope.allDomains = [];
            $scope.domains = [];
            $scope.domain = undefined; // The domain being edited
            $scope.editMode = 'add';
            $scope.search = '';
            $scope.timeZones = moment.tz.names();


            /** Computes the Keycloak URL */
            $scope.getKeycloakUrl = function() {
                // Template http://localhost:8080/auth/admin/master/console/#/realms/niord/clients
                var url = AuthService.keycloak.authServerUrl;
                if (url.charAt(url.length - 1) !== '/') {
                    url += '/';
                }
                return url + 'admin/master/console/#/realms/niord/clients';
            };
            $scope.keycloakUrl = $scope.getKeycloakUrl();


            /** Loads the domains from the back-end */
            $scope.loadDomains = function() {
                $scope.domain = undefined;
                AdminDomainService
                    .getDomains()
                    .success(function (domains) {
                        $scope.allDomains = domains;
                        $scope.searchUpdated();
                    });
            };


            /** Returns if the string matches the given domain property */
            function match(domainProperty, str) {
                var txt = (domainProperty) ? "" + domainProperty : "";
                return txt.toLowerCase().indexOf(str.toLowerCase()) >= 0;
            }


            /** Called whenever search criteria changes */
            $scope.searchUpdated = function() {
                var search = $scope.search.toLowerCase();
                $scope.domains = $scope.allDomains.filter(function (domain) {
                    return match(domain.domainId, search) ||
                        match(domain.name, search);
                });
            };
            $scope.$watch("search", $scope.searchUpdated, true);


            /** Adds a new domain **/
            $scope.addDomain = function () {
                $scope.editMode = 'add';
                $scope.domain = {
                    domainId: undefined,
                    active: true,
                    sortOrder: 0,
                    name: undefined,
                    timeZone: moment.tz.guess(),
                    messageSortOrder: 'AREA ASC',
                    publish: false,
                    atons: false
                };
            };


            /** Copies a domain **/
            $scope.copyDomain = function (domain) {
                $scope.editMode = 'add';
                $scope.domain = angular.copy(domain);
                $scope.domain.domainId = undefined;
            };


            /** Edits a domain **/
            $scope.editDomain = function (domain) {
                $scope.editMode = 'edit';
                $scope.domain = angular.copy(domain);
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving domain", { ttl: 5000 });
            };


            /** Saves the current domain being edited */
            $scope.saveDomain = function () {

                if ($scope.domain && $scope.editMode === 'add') {
                    AdminDomainService
                        .createDomain($scope.domain)
                        .success($scope.loadDomains)
                        .error($scope.displayError);
                } else if ($scope.domain && $scope.editMode === 'edit') {
                    AdminDomainService
                        .updateDomain($scope.domain)
                        .success($scope.loadDomains)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given domain */
            $scope.deleteDomain = function (domain) {
                DialogService.showConfirmDialog(
                    "Delete domain?", "Delete domain ID '" + domain.domainId + "'?")
                    .then(function() {
                        AdminDomainService
                            .deleteDomain(domain)
                            .success($scope.loadDomains)
                            .error($scope.displayError);
                    });
            };


            /** Creates the domain in Keycloak **/
            $scope.createInKeycloak = function (domain) {
                AdminDomainService
                    .createDomainInKeycloak(domain)
                    .success($scope.loadDomains)
                    .error($scope.displayError);
            };


            /** Opens the upload-domains dialog **/
            $scope.uploadDomainsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Domains File',
                    '/rest/domains/upload-domains',
                    'json');
            };
        }]);
