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
            // Used to ensure that description entities have a "subject" field
            function ensureSubjectField(desc) {
                desc.subject = '';
            }


            /** Adds a new mailing list **/
            $scope.addMailingList = function () {
                $scope.editMode = 'add';
                $scope.mailingList = {
                    mailingListId: undefined,
                    active: true,
                    triggers: [],
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
                        $scope.mailingList.triggers = $scope.mailingList.triggers || [];
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
            /** Mailing list triggers       **/
            /** *************************** **/

            /** Called before rendering a trigger **/
            $scope.initTrigger = function(trigger) {
                LangService.checkDescs(trigger, ensureSubjectField);

                // Create a status map
                trigger.statusMap = { 'PUBLISHED':false, "CANCELLED": false, "EXPIRED": false };
                if (trigger.statusChanges) {
                    angular.forEach(trigger.statusChanges, function (status) {
                        trigger.statusMap[status] = true;
                    })
                }
            };


            /** Adds a new mailing list trigger to the mailing list **/
            $scope.addTrigger = function (mailingList, trigger) {
                trigger = trigger || {
                    edit: true,
                    type: 'STATUS_CHANGE',
                    statusChanges: [ 'PUBLISHED' ],
                    messageQuery: '',
                    messageFilter: '',
                    scriptResourcePaths: [],
                    descs: []
                };
                LangService.checkDescs(trigger, ensureSubjectField);
                mailingList.triggers.push(trigger);
            };


            /** Copies the mailing list trigger **/
            $scope.copyTrigger = function (mailingList, trigger) {
                $scope.addTrigger(mailingList, angular.copy(trigger))
            };


            /** Deletes the mailing list trigger **/
            $scope.deleteTrigger = function (mailingList, trigger) {
                var index = mailingList.triggers.indexOf(trigger);
                if (index !== -1) {
                    mailingList.triggers.splice(index, 1);
                }
            };


            /** Called whenever the user changes the trigger type **/
            $scope.updateTriggerType = function (trigger) {
                if (trigger.type === 'STATUS_CHANGE') {
                    delete trigger.scheduleType;
                    delete trigger.scheduledTimeOfDay;
                    trigger.statusChanges = [];
                } else if (trigger.type === 'SCHEDULED') {
                    trigger.scheduleType = 'DAILY';
                    trigger.scheduledTimeOfDay = moment().valueOf();
                    delete trigger.statusChanges;
                }
            };


            /** Called whenever the user changes the status changes **/
            $scope.updateStatusChange = function (trigger) {
                // Update the statusChanges list from the statusMap updated via the UI
                trigger.statusChanges = [];
                angular.forEach(trigger.statusMap, function (val, status) {
                    if (val) {
                        trigger.statusChanges.push(status);
                    }
                })
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


            /** **************************** **/
            /** Export / import mailing list **/
            /** **************************** **/


            /** Generate an export file */
            $scope.exportMailingLists = function () {
                AdminMailingListService
                    .exportTicket('sysadmin')
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/mailing-lists/export?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Opens the upload-mailing lists dialog **/
            $scope.uploadMailingListsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Mailing-Lists File',
                    '/rest/mailing-lists/upload-mailing-lists',
                    'json');
            };


        }]);
