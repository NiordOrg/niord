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
     * MailingListAdminCtrl
     * ********************************************************************************
     * MailingLists Admin Controller
     * Controller for the Admin mailing lists page
     */
    .controller('MailingListAdminCtrl', ['$scope', '$rootScope', 'growl', 'LangService',
                'AdminMailingListService', 'DialogService', 'UploadFileService',
        function ($scope, $rootScope, growl, LangService,
                  AdminMailingListService, DialogService, UploadFileService) {
            'use strict';

            $scope.mailingLists = [];
            $scope.mailingList = undefined; // The mailing list being edited
            $scope.editMode = 'add';
            $scope.search = '';
            $scope.hasRole = $rootScope.hasRole;


            /** Loads the mailing lists from the back-end */
            $scope.loadMailingLists = function() {
                $scope.mailingList = undefined;
                AdminMailingListService
                    .getMailingLists()
                    .success(function (mailingLists) {
                        $scope.mailingLists = mailingLists;
                    });
            };


            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
                desc.description = '';
            }

            /** Adds a new mailing list **/
            $scope.addMailingList = function () {
                $scope.editMode = 'add';
                $scope.mailingList = {
                    mailingListId: undefined,
                    users: [],
                    contacts: []
                };
                LangService.checkDescs($scope.mailingList, ensureNameField);
            };


            /** Copies a mailing list **/
            $scope.copyMailingList = function (mailingList) {
                $scope.enterEditMode(mailingList, 'add');
            };


            /** Edits a mailing list **/
            $scope.editMailingList = function (mailingList) {
                $scope.enterEditMode(mailingList, 'edit');
            };


            /** Enter editor mode for the mailing list **/
            $scope.enterEditMode = function (mailingList, action) {
                $scope.editMode = action;
                AdminMailingListService
                    .getMailingList(mailingList.mailingListId)
                    .success(function (mailingListDetails) {
                        $scope.mailingList = mailingListDetails;
                        LangService.checkDescs($scope.mailingList, ensureNameField);
                        if (action === 'add') {
                            $scope.mailingList.mailingListId = undefined;
                        }
                    });
            };


            /** Manage mailing list recipients **/
            $scope.manageRecipients = function (mailingList) {
                $scope.editMode = 'recipients';
                $scope.mailingList = mailingList;
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving mailing list", { ttl: 5000 });
            };


            /** Saves the current mailing list being edited */
            $scope.saveMailingList = function () {

                if ($scope.mailingList && $scope.editMode === 'add') {
                    AdminMailingListService
                        .createMailingList($scope.mailingList)
                        .success($scope.loadMailingLists)
                        .error($scope.displayError);
                } else if ($scope.mailingList && $scope.editMode === 'edit') {
                    AdminMailingListService
                        .updateMailingList($scope.mailingList)
                        .success($scope.loadMailingLists)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given mailing list */
            $scope.deleteMailingList = function (mailingList) {
                DialogService.showConfirmDialog(
                    "Delete mailing list?", "Delete mailing list ID '" + mailingList.mailingListId + "'?")
                    .then(function() {
                        AdminMailingListService
                            .deleteMailingList(mailingList)
                            .success($scope.loadMailingLists)
                            .error($scope.displayError);
                    });
            };


            /** ************** Recipients ************/


            /** Returns a filtered array of recipients */
            function filterRecipients(recipients, filter) {
                if (filter === undefined || filter.length === 0) {
                    return recipients;
                }
                filter = filter.toLowerCase();
                var result = [];
                for (var x = 0; x < recipients.length; x++) {
                    var r = recipients[x];
                    if ((r.email && r.email.toLowerCase().indexOf(filter) !== -1) ||
                        (r.firstName && r.firstName.toLowerCase().indexOf(filter) !== -1) ||
                        (r.lastName && r.lastName.toLowerCase().indexOf(filter) !== -1)) {
                        result.push(r);
                    }
                }
                return result;
            }


            /** ************** Recipient Users ************/

            $scope.userFilter = { selectedFilter: '', availableFilter: '' };
            $scope.recipientUsers = {
                selectedRecipients: [],
                availableRecipients: []
            };

            $scope.filterRecipientUsers = function () {
                $scope.recipientUsers.selected = filterRecipients(
                    $scope.recipientUsers.selectedRecipients,
                    $scope.userFilter.selectedFilter
                );
                $scope.recipientUsers.available = filterRecipients(
                    $scope.recipientUsers.availableRecipients,
                    $scope.userFilter.availableFilter
                );
            };


            /** Initialize the recipient user filter **/
            $scope.initUserFilter = function () {
                $scope.userFilter.selectedFilter = '';
                $scope.userFilter.availableFilter = '';
            };


            /** Loads the recipient users, i.e. the selected and available users for the mailing list **/
            $scope.loadRecipientUsers = function (mailingList) {
                AdminMailingListService
                    .getRecipientUsers(mailingList)
                    .success(function (recipientUsers) {
                        $scope.recipientUsers = recipientUsers;
                        $scope.filterRecipientUsers();
                    })
            };


            /** Updates the recipient users **/
            $scope.updateRecipientUsers = function (mailingList) {
                AdminMailingListService
                    .updateRecipientUsers(mailingList, $scope.recipientUsers.selectedRecipients)
                    .success(function (recipientUsers) {
                        $scope.recipientUsers = recipientUsers;
                        $scope.filterRecipientUsers();
                    })
            };


            /** Adds the given user as a recipient **/
            $scope.addRecipientUser = function (mailingList, user) {
                $scope.recipientUsers.selectedRecipients.push(user);
                $scope.updateRecipientUsers(mailingList);
            };


            /** Removes the given user as a recipient **/
            $scope.removeRecipientUser = function (mailingList, user) {
                var index = $scope.recipientUsers.selectedRecipients.indexOf(user);
                $scope.recipientUsers.selectedRecipients.splice(index, 1);
                $scope.updateRecipientUsers(mailingList);
            };


            /** Adds all available users as recipients **/
            $scope.addAllRecipientUsers = function (mailingList) {
                var r = $scope.recipientUsers;
                r.selectedRecipients = Array.prototype.concat.apply(r.selectedRecipients, r.availableRecipients);
                $scope.updateRecipientUsers(mailingList);
            };


            /** Removes all recipient users **/
            $scope.removeAllRecipientUsers = function (mailingList) {
                $scope.recipientUsers.selectedRecipients.length = 0;
                $scope.updateRecipientUsers(mailingList);
            };

            $scope.$watch("userFilter", $scope.filterRecipientUsers, true);


            /** ************** Recipient Contacts ************/

            $scope.contactFilter = { selectedFilter: '', availableFilter: '' };
            $scope.recipientContacts = {
                selectedRecipients: [],
                availableRecipients: []
            };

            $scope.filterRecipientContacts = function () {
                $scope.recipientContacts.selected = filterRecipients(
                    $scope.recipientContacts.selectedRecipients,
                    $scope.contactFilter.selectedFilter
                );
                $scope.recipientContacts.available = filterRecipients(
                    $scope.recipientContacts.availableRecipients,
                    $scope.contactFilter.availableFilter
                );
            };


            /** Initialize the recipient contact filter **/
            $scope.initContactFilter = function () {
                $scope.contactFilter.selectedFilter = '';
                $scope.contactFilter.availableFilter = '';
            };


            /** Loads the recipient contacts, i.e. the selected and available contacts for the mailing list **/
            $scope.loadRecipientContacts = function (mailingList) {
                AdminMailingListService
                    .getRecipientContacts(mailingList)
                    .success(function (recipientContacts) {
                        $scope.recipientContacts = recipientContacts;
                        $scope.filterRecipientContacts();
                    })
            };


            /** Updates the recipient contacts **/
            $scope.updateRecipientContacts = function (mailingList) {
                AdminMailingListService
                    .updateRecipientContacts(mailingList, $scope.recipientContacts.selectedRecipients)
                    .success(function (recipientContacts) {
                        $scope.recipientContacts = recipientContacts;
                        $scope.filterRecipientContacts();
                    })
            };


            /** Adds the given contact as a recipient **/
            $scope.addRecipientContact = function (mailingList, contact) {
                $scope.recipientContacts.selectedRecipients.push(contact);
                $scope.updateRecipientContacts(mailingList);
            };


            /** Removes the given contact as a recipient **/
            $scope.removeRecipientContact = function (mailingList, contact) {
                var index = $scope.recipientContacts.selectedRecipients.indexOf(contact);
                $scope.recipientContacts.selectedRecipients.splice(index, 1);
                $scope.updateRecipientContacts(mailingList);
            };


            /** Adds all available contacts as recipients **/
            $scope.addAllRecipientContacts = function (mailingList) {
                var r = $scope.recipientContacts;
                r.selectedRecipients = Array.prototype.concat.apply(r.selectedRecipients, r.availableRecipients);
                $scope.updateRecipientContacts(mailingList);
            };


            /** Removes all recipient contacts **/
            $scope.removeAllRecipientContacts = function (mailingList) {
                $scope.recipientContacts.selectedRecipients.length = 0;
                $scope.updateRecipientContacts(mailingList);
            };

            $scope.$watch("contactFilter", $scope.filterRecipientContacts, true);

        }]);
