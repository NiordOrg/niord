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
                    active: true,
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


            /** *************************** **/
            /** Mailing list recipients     **/
            /** *************************** **/


            $scope.recipientUsers = {
                selectedRecipients: [],
                availableRecipients: []
            };
            $scope.recipientContacts = {
                selectedRecipients: [],
                availableRecipients: []
            };

            /** Loads the recipient users, i.e. the selected and available users for the mailing list **/
            $scope.loadRecipientUsers = function (mailingList) {
                AdminMailingListService
                    .getRecipientUsers(mailingList)
                    .success(function (recipientUsers) {
                        $scope.recipientUsers = recipientUsers;
                    })
            };


            /** Updates the recipient users **/
            $scope.updateRecipientUsers = function (mailingList) {
                AdminMailingListService
                    .updateRecipientUsers(mailingList, $scope.recipientUsers.selectedRecipients)
                    .success(function () {
                        $scope.loadMailingLists();
                    })
            };


            /** Loads the recipient contacts, i.e. the selected and available contacts for the mailing list **/
            $scope.loadRecipientContacts = function (mailingList) {
                AdminMailingListService
                    .getRecipientContacts(mailingList)
                    .success(function (recipientContacts) {
                        $scope.recipientContacts = recipientContacts;
                    })
            };


            /** Updates the recipient contacts **/
            $scope.updateRecipientContacts = function (mailingList) {
                AdminMailingListService
                    .updateRecipientContacts(mailingList, $scope.recipientContacts.selectedRecipients)
                    .success(function () {
                        $scope.loadMailingLists();
                    })
            };

        }]);
