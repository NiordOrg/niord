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
    .controller('UserAdminCtrl', ['$scope', '$rootScope', 'growl', 'AdminUserService', 'AuthService', 'DialogService',
        function ($scope, $rootScope, growl, AdminUserService, AuthService, DialogService) {
            'use strict';

            $scope.groups = [];
            $scope.group = undefined;
            $scope.users = [];
            $scope.user = undefined;
            $scope.groupUser = '';
            $scope.action = "edit";
            $scope.userFilter = '';
            $scope.pageSize = 20;
            $scope.hasMore = false;
            $scope.languages = $rootScope.modelLanguages;


            /** Init the controller **/
            $scope.init = function () {
                $scope.loadGroups();

                // Computes the Keycloak URL
                // Template: http://localhost:8080/auth/admin/master/console/#/realms/niord/clients
                $scope.keycloakUrl = AuthService.keycloak.authServerUrl;
                if ($scope.keycloakUrl.charAt($scope.keycloakUrl.length - 1) !== '/') {
                    $scope.keycloakUrl += '/';
                }
                $scope.keycloakUrl += 'admin/master/console/#/realms/niord/clients';
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("User operation failed", { ttl: 5000 });
            };


            /** If the group form is visible set it to be pristine */
            $scope.setPristine = function () {
                if ($scope.userForm) {
                    $scope.userForm.$setPristine();
                }
            };


            /** Searches the list of users **/
            $scope.loadUsers = function (append) {
                if (!append) {
                    $scope.users.length = 0;
                    $scope.hasMore = false;
                }

                // Load one more than the page size to determine if the user should be allowed to load more
                AdminUserService.getUsers($scope.userFilter, $scope.users.length, $scope.pageSize + 1)
                    .success(function (users) {
                        for (var x = 0; x < Math.min(users.length, $scope.pageSize); x++) {
                            $scope.users.push(users[x]);
                        }
                        $scope.hasMore = users.length > $scope.pageSize;
                    })
                    .error($scope.displayError);
            };


            /** Called when the user filter is updated **/
            $scope.updateUserFilter = function () {
                $scope.loadUsers(false);
            };
            $scope.$watch("userFilter", $scope.updateUserFilter, true);


            /** Loads the group tree from Keycloak **/
            $scope.loadGroups = function () {
                AdminUserService.getGroups()
                    .success(function (groups) {
                        $scope.groups = groups;
                    })
                    .error($scope.displayError);
            };


            /** Creates a new user */
            $scope.addUser = function() {
                $scope.action = "add";
                $scope.user = {
                    keycloakId: '',
                    username: '',
                    email: '',
                    firstName: '',
                    lastName: '',
                    language: undefined,
                    action: {
                        'UPDATE_PROFILE': false,
                        'UPDATE_PASSWORD': false,
                        'VERIFY_EMAIL': false
                    }
                };
                $scope.group = undefined;
                $scope.setPristine();
            };


            /** Edits a user */
            $scope.editUser = function(user) {
                $scope.action = "edit";
                $scope.user = angular.copy(user);
                $scope.group = undefined;
                AdminUserService.getUserGroups(user.keycloakId)
                    .success(function (groups) {
                        $scope.user.groups = groups;
                        $scope.setPristine();
                    })
                    .error($scope.displayError);
            };


            /** Cancels editing users **/
            $scope.cancelEdit = function () {
                $scope.user = undefined;
                $scope.group = undefined;
                $scope.loadUsers();
            };


            /** Saves the currently edited user **/
            $scope.saveUser = function () {
                if ($scope.user && $scope.action === 'add') {
                    $scope.user.keycloakActions = [];
                    angular.forEach($scope.user.action, function (value, key) {
                        if (value) {
                            $scope.user.keycloakActions.push(key);
                        }
                    });
                    if ($scope.user.language === '') {
                        delete $scope.user.language;
                    }

                    AdminUserService.addUser($scope.user)
                        .success(function () {
                            growl.info("User added", { ttl: 3000 });
                            $scope.cancelEdit();
                        })
                        .error($scope.displayError);
                } else if ($scope.user) {
                    if ($scope.user.language === '') {
                        delete $scope.user.language;
                    }
                    AdminUserService.updateUser($scope.user)
                        .success(function () {
                            growl.info("User updated", { ttl: 3000 });
                            $scope.cancelEdit();
                        })
                        .error($scope.displayError);
                }
            };


            /** Deletes the given user in Keycloak **/
            $scope.deleteUser = function (user) {
                DialogService.showConfirmDialog(
                    "Delete user?", "Delete user \"" + user.username + "\" in Keycloak?\n")
                    .then(function() {
                        AdminUserService.deleteUser(user.keycloakId)
                            .success(function () {
                                growl.info("User deleted", { ttl: 3000 });
                                $scope.loadUsers();
                            })
                            .error($scope.displayError);
                    });
            };


            /** Called when a group is selected */
            $scope.selectGroup = function (group) {
                $scope.group = group && group.active ? group : undefined;
                $scope.$$phase || $scope.$apply();
            };


            /** Called when a group has been dragged to a new parent group */
            $scope.moveGroup = function () {
                alert("Not supported. Please use Keycloak");
                $scope.loadGroups();
                $scope.$$phase || $scope.$apply();
            };


            /** Let the user join the given group **/
            $scope.joinGroup = function () {
                AdminUserService.joinUserGroup($scope.user.keycloakId, $scope.group.id)
                    .success(function () {
                        growl.info("User joined group", { ttl: 3000 });
                        $scope.editUser($scope.user);
                    })
                    .error($scope.displayError);
            };


            /** Let the user leave the given group **/
            $scope.leaveGroup = function (group) {
                AdminUserService.leaveUserGroup($scope.user.keycloakId, group.id)
                    .success(function () {
                        growl.info("User left group", { ttl: 3000 });
                        $scope.editUser($scope.user);
                    })
                    .error($scope.displayError);
            }
        }])


    /**
     * ********************************************************************************
     * ContactAdminCtrl
     * ********************************************************************************
     * Contacts Admin Controller
     * Controller for the Admin contacts page
     */
    .controller('ContactAdminCtrl', ['$scope', '$rootScope', 'growl', 'AdminContactService', 'AuthService', 'DialogService',
        function ($scope, $rootScope, growl, AdminContactService, AuthService, DialogService) {
            'use strict';

            $scope.contact = undefined;
            $scope.action = "edit";
            $scope.languages = $rootScope.modelLanguages;

            $scope.params = {
                name: ''
            };
            $scope.pageData = {
                page: 1,
                maxSize: 20
            };
            $scope.searchResult = {
                data: [],
                size: 0,
                total: 0
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Contact operation failed", { ttl: 5000 });
            };


            /** Set the contact to be pristine */
            $scope.setPristine = function () {
                if ($scope.contactForm) {
                    $scope.contactForm.$setPristine();
                }
            };


            /** Searches the list of contacts **/
            $scope.searchContacts = function () {
                AdminContactService
                    .searchContacts($scope.params, $scope.pageData)
                        .success(function (searchResult) {
                            $scope.searchResult = searchResult;
                        })
                    .error($scope.displayError);
            };


            // Monitor params and page
            $scope.$watch("params", function () {
                $scope.pageData.page = 1;
                $scope.searchContacts();
            }, true);
            $scope.$watch("pageData", $scope.searchContacts, true);


            /** Creates a new contact */
            $scope.addContact = function() {
                $scope.action = "add";
                $scope.contact = {
                    email: '',
                    firstName: '',
                    lastName: '',
                    language: undefined
                };
                $scope.setPristine();
            };


            /** Edits a contact */
            $scope.editContact = function(contact) {
                $scope.action = "edit";
                $scope.contact = angular.copy(contact);
                $scope.setPristine();
            };


            /** Cancels editing contacts **/
            $scope.cancelEdit = function () {
                $scope.contact = undefined;
                $scope.searchContacts();
            };


            /** Saves the currently edited contact **/
            $scope.saveContact = function () {
                if ($scope.contact.language === '') {
                    delete $scope.contact.language;
                }

                if ($scope.action === 'add') {
                    AdminContactService.createContact($scope.contact)
                        .success(function () {
                            growl.info("Contact added", { ttl: 3000 });
                            $scope.cancelEdit();
                        })
                        .error($scope.displayError);

                } else if ($scope.action === 'edit') {
                    AdminContactService.updateContact($scope.contact)
                        .success(function () {
                            growl.info("Contact updated", { ttl: 3000 });
                            $scope.cancelEdit();
                        })
                        .error($scope.displayError);
                }
            };


            /** Deletes the given contact **/
            $scope.deleteContact = function (contact) {
                DialogService.showConfirmDialog(
                    "Delete contact?", "Delete contact \"" + contact.email + "\"?\n")
                    .then(function() {
                        AdminContactService.deleteContact(contact)
                            .success(function () {
                                growl.info("Contact deleted", { ttl: 3000 });
                                $scope.searchContacts();
                            })
                            .error($scope.displayError);
                    });
            };


            /** Generate an export file */
            $scope.exportContacts = function () {
                AdminContactService
                    .exportTicket('admin')
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/contacts/export?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Import a textual list of email addresses as new contacts */
            $scope.importContacts = function () {
                var modalOptions = {
                    closeButtonText: 'Cancel',
                    actionButtonText: 'Import',
                    headerText: 'Import E-mails',
                    importOptions: { emails: '' },
                    templateUrl: "importEmails.html"
                };

                DialogService.showDialog({}, modalOptions)
                    .then(function () {
                        var emails = modalOptions.importOptions.emails;
                        if (emails) {
                            AdminContactService
                                .importContactEmails(emails)
                                .success(function (result) {
                                    growl.info(result, { ttl: 3000 });
                                    $scope.searchContacts();
                                })
                                .error($scope.displayError);
                        }
                    });
            };

        }]);

