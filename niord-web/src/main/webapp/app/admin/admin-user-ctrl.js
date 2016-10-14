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
     * UserAdminCtrl
     * ********************************************************************************
     * Users Admin Controller
     * Controller for the Admin users page
     */
    .controller('UserAdminCtrl', ['$scope', 'growl', 'LangService', 'AuthService',
        function ($scope, growl, LangService, AuthService) {
            'use strict';


            $scope.loadUsers = function () {
                console.log("LOADING USERS");
            };

        }])

    /**
     * ********************************************************************************
     * GroupAdminCtrl
     * ********************************************************************************
     * Groups Admin Controller
     * Controller for the Admin user groups page
     */
    .controller('GroupAdminCtrl', ['$scope', 'growl', 'AdminUserService',
        function ($scope, growl, AdminUserService) {
            'use strict';

            $scope.groups = [];
            $scope.group = undefined;
            $scope.editGroup = undefined;
            $scope.groupFilter = '';
            $scope.action = "edit";


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Group operation failed", { ttl: 5000 });
            };


            /** If the group form is visible set it to be pristine */
            $scope.setPristine = function () {
                if ($scope.groupForm) {
                    $scope.groupForm.$setPristine();
                }
            };

            /** Loads the group tree from Keycloak **/
            $scope.loadGroups = function () {
                AdminUserService.getGroups()
                    .success(function (groups) {
                        $scope.groups = groups;
                    })
                    .error($scope.displayError);
            };


            /** Creates a new group */
            $scope.newGroup = function() {
                $scope.action = "add";
                $scope.editGroup = { descs: [ { lang: 'en', name: '' }] };
                if ($scope.group) {
                    $scope.editGroup.parent = { id: $scope.group.id };
                }
                $scope.setPristine();
            };


            /** Called when a group is selected */
            $scope.selectGroup = function (group) {
                AdminUserService.getGroupsRoles(group)
                    .success(function (roles) {
                        $scope.action = "edit";
                        $scope.group = group;
                        $scope.editGroup = angular.copy($scope.group);
                        $scope.editGroup.roles = roles;
                        $scope.setPristine();
                        $scope.$$phase || $scope.$apply();
                    })
                    .error($scope.displayError);
            };


            /** Called when a group has been dragged to a new parent group */
            $scope.moveGroup = function () {
                alert("Not supported. Please use Keycloak");
                $scope.loadGroups();
                $scope.$$phase || $scope.$apply();
            };

        }]);

