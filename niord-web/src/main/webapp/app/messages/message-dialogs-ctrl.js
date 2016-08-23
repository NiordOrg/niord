
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
 * Controllers for message list dialogs
 */
angular.module('niord.messages')

    /*******************************************************************
     * Controller that handles displaying message details in a dialog
     *******************************************************************/
    .controller('MessageDialogCtrl', ['$scope', '$window', 'growl', 'MessageService', 'messageId', 'messages', 'selection',
        function ($scope, $window, growl, MessageService, messageId, messages, selection) {
            'use strict';

            $scope.warning = undefined;
            $scope.messages = messages;
            $scope.pushedMessageIds = [];
            $scope.pushedMessageIds[0] = messageId;
            $scope.selection = selection;

            $scope.msg = undefined;
            $scope.index = $.inArray(messageId, messages);
            $scope.showNavigation = $scope.index >= 0;
            $scope.showMap = true;

            // Attempt to improve printing
            $("body").addClass("no-print");
            $scope.$on("$destroy", function() {
                $("body").removeClass("no-print");
            });

            // Navigate to the previous message in the message list
            $scope.selectPrev = function() {
                if ($scope.pushedMessageIds.length == 1 && $scope.index > 0) {
                    $scope.index--;
                    $scope.pushedMessageIds[0] = $scope.messages[$scope.index];
                    $scope.loadMessageDetails();
                }
            };

            // Navigate to the next message in the message list
            $scope.selectNext = function() {
                if ($scope.pushedMessageIds.length == 1 && $scope.index >= 0 && $scope.index < $scope.messages.length - 1) {
                    $scope.index++;
                    $scope.pushedMessageIds[0] = $scope.messages[$scope.index];
                    $scope.loadMessageDetails();
                }
            };

            // Navigate to a new nested message
            $scope.selectMessage = function (messageId) {
                $scope.pushedMessageIds.push(messageId);
                $scope.loadMessageDetails();
            };

            // Navigate back in the nested navigation
            $scope.back = function () {
                if ($scope.pushedMessageIds.length > 1) {
                    $scope.pushedMessageIds.pop();
                    $scope.loadMessageDetails();
                }
            };

            // Return the currently diisplayed message id
            $scope.currentMessageId = function() {
                return $scope.pushedMessageIds[$scope.pushedMessageIds.length - 1];
            };

            // Load the message details for the given message id
            $scope.loadMessageDetails = function() {

                MessageService.details($scope.currentMessageId())
                    .success(function (data) {
                        $scope.warning = (data) ? undefined : "Message " + $scope.currentMessageId() + " not found";
                        $scope.msg = data;
                        $scope.showMap = true;
                        if ($scope.msg.attachments) {
                            var attachmentsAbove = $.grep($scope.msg.attachments, function (att) {
                                return att.display == 'ABOVE';
                            });
                            if (attachmentsAbove.length > 0) {
                                $scope.showMap = false;
                            }
                        }

                    })
                    .error(function () {
                        $scope.msg = undefined;
                        growl.error('Message Lookup Failed', { ttl: 5000 });
                    });
            };

            $scope.loadMessageDetails();


            // Returns if the given message is selected or not
            $scope.isSelected = function () {
                return $scope.selection && $scope.currentMessageId() &&
                    $scope.selection.get($scope.currentMessageId()) !== undefined;
            };


            // Toggle the selection state of the message
            $scope.toggleSelectMessage = function () {
                if ($scope.isSelected()) {
                    $scope.selection.remove($scope.currentMessageId());
                } else if ($scope.msg && $scope.selection) {
                    $scope.selection.put($scope.currentMessageId(), angular.copy($scope.msg));
                }
            };


            // Returns the select button class
            $scope.selectBtnClass = function () {
                return $scope.isSelected() ? "btn-danger" : "btn-default";
            };
            
        }])


    /*******************************************************************
     * Controller that handles displaying message tags in a dialog
     *******************************************************************/
    .controller('MessageTagsDialogCtrl', ['$scope', '$timeout', 'growl', 'MessageService', 'AuthService',
        function ($scope, $timeout, growl, MessageService, AuthService) {
            'use strict';

            $scope.isLoggedIn = AuthService.loggedIn;
            $scope.search = '';
            $scope.data = {
                tags : []
            };

            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error accessing tags", { ttl: 5000 });
            };


            // Load the message tags for the current user
            $scope.loadTags = function() {
                delete $scope.data.editTag;
                delete $scope.data.editMode;
                MessageService.tags()
                    .success(function (tags) {
                        $scope.data.tags.length = 0;
                        angular.forEach(tags, function (tag) {
                            $scope.data.tags.push(tag);
                        });
                    })
                    .error($scope.displayError);
            };
            $scope.loadTags();

            // Set focus to the tag filter input field
            $timeout(function () { $('#tagIdFilter').focus() }, 200);

            // Adds a new tag
            $scope.addTag = function () {
                $scope.data.editMode = 'add';
                $scope.data.editTag = { tagId: '', name: '', type: 'PRIVATE', expiryDate: undefined };
            };


            // Edits an existing tag
            $scope.editTag = function (tag) {
                $scope.data.editMode = 'edit';
                $scope.data.editTag = angular.copy(tag);
            };


            // Edits an existing tag
            $scope.copyTag = function (tag) {
                $scope.data.editMode = 'add';
                $scope.data.editTag = angular.copy(tag);
            };


            // Deletes the given tag
            $scope.deleteTag = function (tag) {
                MessageService
                    .deleteMessageTag(tag)
                    .success($scope.loadTags)
                    .error($scope.displayError);
            };


            // Clears messages from the given tag
            $scope.clearTag = function (tag) {
                MessageService
                    .clearMessageTag(tag)
                    .success($scope.loadTags)
                    .error($scope.displayError);
            };


            // Saves the tag being edited
            $scope.saveTag = function () {
                if ($scope.data.editTag && $scope.data.editMode == 'add') {
                    MessageService
                        .createMessageTag($scope.data.editTag)
                        .success($scope.loadTags)
                        .error($scope.displayError);
                } else if ($scope.data.editTag && $scope.data.editMode == 'edit') {
                    MessageService
                        .updateMessageTag($scope.data.editTag)
                        .success($scope.loadTags)
                        .error($scope.displayError);
                }
            };


            // Navigate to the given link
            $scope.navigateTag = function (tag) {
                MessageService.saveLastMessageTagSelection(tag);
                $scope.$close(tag);
            };

        }])



    /*******************************************************************
     * Controller that handles the message Print dialog
     *******************************************************************/
    .controller('MessagePrintDialogCtrl', ['$scope', '$document', '$window', 'MessageService', 'total', 'reports',
        function ($scope, $document, $window, MessageService, total, reports) {
            'use strict';

            $scope.totalMessageNo = total;
            $scope.reports = undefined;

            $scope.data = {
                report: undefined,
                pageSize : 'A4',
                pageOrientation: 'portrait'
            };

            if ($window.localStorage.printSettings) {
                try {
                    angular.copy(angular.fromJson($window.localStorage.printSettings), $scope.data);
                } catch (error) {
                }
            }

            // If requested, load the message reports
            if (reports) {
                MessageService.printReports()
                    .success(function (reports) {
                        $scope.reports = reports;
                        if (reports.length > 0) {
                            $scope.data.report = reports[0].reportId;
                        }
                    })
            }

            // Register and unregister event-handler to listen for return key
            var eventTypes = "keydown keypress";
            function eventHandler(event) {
                if (event.which === 13) {
                    $scope.print();
                }
            }
            $document.bind(eventTypes, eventHandler);
            $scope.$on('$destroy', function () {
                $document.unbind(eventTypes, eventHandler);
            });

            // Close the print dialog and return the print settings to the callee
            $scope.print = function () {
                $window.localStorage.printSettings = angular.toJson($scope.data);
                $scope.$close($scope.data);
            };

        }])



    /*******************************************************************
     * Controller that handles the message send-mail dialog
     *******************************************************************/
    .controller('MessageMailDialogCtrl', ['$scope', 'growl', 'MessageService', 'total', 'params',
        function ($scope, growl, MessageService, total, params) {
            'use strict';

            $scope.totalMessageNo = total;

            $scope.data = {
                mailTo : '',
                mailSubject: '',
                mailMessage: ''
            };


            /** Sends the e-mail and closes the dialog **/
            $scope.sendMail = function () {

                var p = params || '';
                if (p.length > 0) {
                    p += '&';
                }
                p = p
                    + '&mailTo=' + encodeURIComponent($scope.data.mailTo)
                    + '&mailSubject=' + encodeURIComponent($scope.data.mailSubject)
                    + '&mailMessage=' + encodeURIComponent($scope.data.mailMessage);

                MessageService
                    .sendMessageMail(p)
                    .success(function () {
                        growl.info("E-mail sent", { ttl: 3000 });
                        $scope.$close("ok");
                    })
                    .error(function () {
                        growl.error("Error sending e-mail", { ttl: 5000 });
                    });
            };

        }])



    /*******************************************************************
     * Controller that handles sorting of messages withing an area
     *******************************************************************/
    .controller('SortAreaDialogCtrl', ['$scope', '$rootScope', '$timeout', 'MessageService',
        function ($scope, $rootScope, $timeout, MessageService) {
            'use strict';

            $scope.data = {
                area: undefined,
                status: 'PUBLISHED'
            };
            $scope.messageList = [];
            $scope.domain = $rootScope.domain !== undefined;


            /** Refreshes the list of messages matching the message filter */
            $scope.refreshMessageList = function () {
                $scope.messageList.length = 0;
                if ($scope.data.area && $scope.data.status) {
                    var params = 'area=' + $scope.data.area.id
                        + '&status=' + $scope.data.status
                        + '&sortBy=AREA&sortOrder=ASC';
                    MessageService.search(params, 0, 1000)
                        .success(function (result) {
                            for (var x = 0; x < result.data.length; x++) {
                                $scope.messageList.push(result.data[x]);
                            }
                            $scope.totalMessageNo = result.total;
                        });
                }
            };
            $scope.$watch("data", $scope.refreshMessageList, true);


            /** Updates the sort order **/
            $scope.updateMessageSortOrder = function (evt) {
                if (evt.newIndex == evt.oldIndex) {
                    return;
                }

                var index = evt.newIndex;
                var id = $scope.messageList[index].id;
                var afterId = index > 0 ? $scope.messageList[index - 1].id : null;
                var beforeId = index < $scope.messageList.length - 1 ? $scope.messageList[index + 1].id : null;
                MessageService.changeAreaSortOrder(id, afterId, beforeId)
                    .success($scope.refreshMessageList);
            };

            
            /** DnD configuration **/
            $scope.messagesSortableCfg = {
                group: 'message',
                handle: '.move-btn',
                onEnd: $scope.updateMessageSortOrder
            };


            // Initially, give focus to the area field
            $timeout(function () {
                $('#area').find('div').controller('uiSelect').activate(false, true);
            }, 100);

        }])


    /*******************************************************************
     * Controller that handles importing of messages
     *******************************************************************/
    .controller('ImportMessagesDialogCtrl', ['$scope', '$rootScope', '$http', 'MessageService',
        function ($scope, $rootScope, $http, MessageService) {
            'use strict';

            $scope.importUrl = '/rest/message-io/import';
            $scope.result = '';

            /** Displays the error message */
            $scope.displayError = function (err) {
                growl.error("Error");
                $scope.result = 'Error:\n' + err;
            };


            // Determine the message series for the current domain
            $scope.messageSeriesIds = [];
            if ($rootScope.domain && $rootScope.domain.messageSeries) {
                angular.forEach($rootScope.domain.messageSeries, function (series) {
                    $scope.messageSeriesIds.push(series.seriesId);
                });
            }

            $scope.data = {
                assignNewUids: false,
                preserveStatus: false,
                createBaseData: false,
                assignDefaultSeries: false,
                seriesId: $scope.messageSeriesIds.length > 0 ? $scope.messageSeriesIds[0] : undefined,
                tagId: ''
            };


            /** Refreshes the tags search result */
            $scope.tags = [];
            $scope.tagData = { tag: undefined };
            $scope.refreshTags = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/tags/search?name=' + encodeURIComponent(name) + '&limit=10'
                ).then(function(response) {
                    $scope.tags = response.data;
                });
            };

            /** Opens the tags dialog */
            $scope.openTagsDialog = function () {
                MessageService.messageTagsDialog().result
                    .then(function (tag) {
                        if (tag) {
                            $scope.tagData.tag = tag;
                        }
                    });
            };

            /** Removes the current tag selection */
            $scope.removeTag = function () {
                $scope.tagData.tag = undefined;
            };


            // Sync the tagData.tag with the data.tagName
            $scope.$watch("tagData", function () {
                $scope.data.tagId = $scope.tagData.tag ? $scope.tagData.tag.tagId : undefined;
            }, true);


            /** Called when the messages zip archive has been imported */
            $scope.fileUploaded = function(result) {
                $scope.result = result;
                $scope.$$phase || $scope.$apply();
            };

            /** Called when the messages zip archive import has failed */
            $scope.fileUploadError = function(status) {
                $scope.result = "Error importing messages (error " + status + ")";
                $scope.$$phase || $scope.$apply();
            };
    

        }])


    /*******************************************************************
     * Controller that handles bulk-updating the status of a message list
     *******************************************************************/
    .controller('UpdateStatusDialogCtrl', ['$scope', 'growl', 'MessageService', 'DialogService', 'selection',
        function ($scope, growl, MessageService, DialogService, selection) {
            'use strict';

            // Valid status transitions.
            // Keep in sync with MessageService.getValidStatusTransitions()
            $scope.validStatuses = {
                'DRAFT':     [ 'VERIFIED', 'DELETED' ],
                'VERIFIED':  [ 'PUBLISHED', 'DRAFT', 'DELETED' ],
                'IMPORTED':  [ 'DRAFT', 'DELETED' ],
                'PUBLISHED': [ 'CANCELLED' ]
            };


            $scope.messageList = [];
            $scope.statuses = {};
            $scope.updatesStatuses = {};

            // Build up list of message that we can change the status of + list of valid status transitions
            angular.forEach(selection.values(), function (message) {
                if ($scope.validStatuses[message.status] !== undefined) {
                    $scope.messageList.push(message);
                    $scope.statuses[message.status] = $scope.validStatuses[message.status];
                }
            });


            // Valid list of from-to status transitions
            $scope.updateStatuses = [];
            angular.forEach($scope.statuses, function (toStatuses, fromStatus) {
               angular.forEach(toStatuses, function (toStatus) {
                   $scope.updateStatuses.push({
                       fromStatus: fromStatus,
                       toStatus: toStatus
                   })
               })
            });

            /** Updates all statuses with the fromStatus to the toStatus **/
            $scope.updateStatus = function (fromStatus, toStatus) {
                angular.forEach($scope.messageList, function (message) {
                   if (message.status == fromStatus) {
                       $scope.updatesStatuses[message.id] = toStatus;
                   }
                });
            };


            /** Saves all the status changes and closes the dialog **/
            $scope.saveChanges = function () {
                var updates = [];
                angular.forEach($scope.updatesStatuses, function (value, key) {
                    if (value !== null) {
                        updates.push({
                            messageId: key,
                            status: value
                        });
                    }
                });
                if (updates.length == 0) {
                    $scope.$dismiss("cancel");
                    return;
                }

                DialogService.showConfirmDialog(
                    "Update Statuses?", "Update the status of " + updates.length + " messages?")
                    .then(function() {

                        MessageService.updateMessageStatuses(updates)
                            .success(function () {
                                growl.info("Updated statuses", { ttl: 3000 });
                                $scope.$close("ok");
                            })
                            .error(function () {
                                growl.error("Error updating statuses", { ttl: 5000 });
                            });
                    });
            }

        }]);
