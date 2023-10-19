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
    .controller('MailingListAdminCtrl', ['$scope', '$rootScope', '$uibModal', 'growl', 'LangService',
                'AdminMailingListService', 'DialogService', 'UploadFileService',
        function ($scope, $rootScope, $uibModal, growl, LangService,
                  AdminMailingListService, DialogService, UploadFileService) {
            'use strict';

            $scope.mailingLists = [];
            $scope.mailingList = undefined; // The mailing list being edited
            $scope.editMode = 'add';
            $scope.search = '';
            $scope.hasRole = $rootScope.hasRole;
            $scope.timeZones = moment.tz.names();


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
                        $scope.mailingList.triggers = $scope.mailingList.triggers || [];
                        angular.forEach($scope.mailingList.triggers, function (trigger) {
                            angular.forEach(trigger.descs || [], function (desc) {
                                desc.enabled = true;
                            });
                        });
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

                // Remove trigger descriptor entities not currently enabled
                angular.forEach($scope.mailingList.triggers, function (trigger) {
                    trigger.descs  = $.grep(trigger.descs, function (desc) { return desc.enabled; })
                });


                if ($scope.editMode === 'add') {
                    AdminMailingListService
                        .createMailingList($scope.mailingList)
                        .success($scope.loadMailingLists)
                        .error($scope.displayError);
                } else if ($scope.editMode === 'edit') {
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


            /** Called when the user enables/disabled a language */
            $scope.updateTriggerLangEnabledState = function (desc) {
                if (!desc.enabled) {
                    desc.subject = '';
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
                angular.forEach(trigger.descs, function (desc) {
                    desc.enabled = true;
                });
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


            /** Deletes the mailing list trigger **/
            $scope.testExecuteTrigger = function(mailingList, index) {
                return $uibModal.open({
                    controller: "ExecuteTriggerDialogCtrl",
                    templateUrl: "/app/admin/mailing-list-test-dialog.html",
                    size: 'lg',
                    resolve: {
                        mailingList: function () { return mailingList; },
                        index: function () { return index; }
                    }
                });
            };


            /** Called whenever the user changes the trigger type **/
            $scope.updateTriggerType = function (trigger) {
                if (trigger.type === 'STATUS_CHANGE') {
                    delete trigger.scheduleType;
                    delete trigger.scheduledExecutionTime;
                    delete trigger.scheduledExecutionTimeZone;
                    trigger.statusChanges = [];
                } else if (trigger.type === 'SCHEDULED') {
                    trigger.scheduleType = 'DAILY';
                    trigger.scheduledExecutionTime = moment().format('HH:mm');
                    trigger.scheduledExecutionTimeZone = $rootScope.domain && $rootScope.domain.timeZone
                                    ? $rootScope.domain.timeZone : moment.tz.guess();
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


        }])


    /**
     * ********************************************************************************
     * ExecuteTriggerDialogCtrl
     * ********************************************************************************
     * Controller for testing mailing list trigger execution
     */
    .controller('ExecuteTriggerDialogCtrl', ['$scope', '$window', 'growl', 'AdminMailingListService', 'mailingList', 'index',
        function ($scope, $window, growl, AdminMailingListService, mailingList, index) {
            'use strict';

            $scope.mailingList = mailingList;
            $scope.trigger = mailingList.triggers[index];

            $scope.mails = [];
            $scope.mailData = { mail: undefined };
            $scope.messageParam = {
                messageId: $scope.trigger.type === 'STATUS_CHANGE' ? $window.localStorage['mailListTestMessage'] : undefined
            };


            /** Checks if the trigger can be executed **/
            $scope.canExecuteTrigger = function () {
                return $scope.trigger.type === 'SCHEDULED' ||  $scope.messageParam.messageId !== undefined;
            };


            /** Test-executes the trigger **/
            $scope.testExecuteTrigger = function() {
                // Remember which message we used for testing
                if ($scope.messageParam.messageId) {
                    $window.localStorage['mailListTestMessage'] = $scope.messageParam.messageId;
                }

                AdminMailingListService
                    .testExecuteTrigger(mailingList, index, $scope.messageParam.messageId)
                    .success(function (mails) {
                        $scope.mails = mails;
                    })
                    .error(function () {
                        growl.error("Error testing trigger", { ttl: 5000 });
                    });
            };

        }])


    /**
     * ********************************************************************************
     * MailingListReportDialogCtrl
     * ********************************************************************************
     * Controller for testing mailing list trigger execution
     */
    .controller('MailingListReportDialogCtrl', ['$scope', '$window', '$timeout', 'growl', 'AdminMailingListService',
        function ($scope, $window, $timeout, growl, AdminMailingListService) {
            'use strict';

            $scope.reports = [];
            AdminMailingListService.mailingListReports()
                .success(function (reports) {
                    $scope.reports = reports;
                });

            $scope.result = undefined;
            $scope.resultReport = '';


            /** Will open the result of executing a report in a new window **/
            $scope.newWindow = function () {
                if (!$scope.resultReport) {
                    return;
                }
                var w = window.open();
                AdminMailingListService
                    .exportTicket('user')
                    .success(function (ticket) {
                        var url = AdminMailingListService.executeMailingListReportUrl($scope.resultReport, ticket);
                        w.document.location.href = url;
                        $timeout(function() {
                            w.document.title = $scope.resultReport.name;
                        }, 500);
                    });
            };


            /** Executes the report **/
            $scope.executeReport = function(report) {
                AdminMailingListService
                    .executeMailingListReport(report)
                    .success(function (result) {
                        $scope.result = result;
                        $scope.resultReport = report;
                    })
                    .error(function () {
                        growl.error("Error executing report", { ttl: 5000 });
                    });
            };

        }]);

