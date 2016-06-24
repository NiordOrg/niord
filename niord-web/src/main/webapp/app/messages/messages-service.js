
/**
 * The message list service
 */
angular.module('niord.messages')

    /**
     * Interface for calling the application server
     */
    .factory('MessageService', [ '$rootScope', '$http', '$window', '$uibModal',
        function($rootScope, $http, $window, $uibModal) {
        'use strict';

        function extractMessageIds(messages) {
            var ids = [];
            if (messages) {
                for (var i in messages) {
                    ids.push(messages[i].id);
                }
            }
            return ids;
        }

        return {

            /** Returns the message filters */
            publicMessages: function() {
                var params = 'lang=' + $rootScope.language;
                if ($rootScope.domain) {
                    params += '&domain=' + $rootScope.domain.clientId;
                }
                return $http.get('/rest/public/v1/messages?' + params);
            },


            /** Returns the message filters */
            search: function(params, page, maxSize) {
                page = page || 0;
                maxSize = maxSize || 1000;
                if (params.length >  0) {
                    params += '&';
                }
                params += 'lang=' + $rootScope.language
                        + '&page=' + page
                        + '&maxSize=' + maxSize;
                return $http.get('/rest/messages/search?' + params);
            },


            /** Returns the message with the given ID */
            details: function (id) {
                return $http.get('/rest/messages/message/' + id + '?lang=' + $rootScope.language);
            },


            /** Returns the editable message with the given ID */
            editableDetails: function (id) {
                return $http.get('/rest/messages/editable-message/' + id + '?lang=' + $rootScope.language);
            },


            /** Returns a new draft message template */
            newMessageTemplate: function (mainType) {
                return $http.get('/rest/messages/new-message-template?mainType=' + mainType);
            },


            /** Returns a draft copy of the given message with the given reference type added to the original */
            copyMessageTemplate: function (id, referenceType) {

                var refParam = (referenceType) ? '&referenceType=' + referenceType : '';
                return $http.get('/rest/messages/copy-message-template/' + id
                            + '?lang=' + $rootScope.language + refParam);
            },


            /** Creates a new or updates an existing message */
            saveMessage: function(msg) {
                if (msg.created) {
                    return $http.put('/rest/messages/message/' + msg.id, msg);
                } else {
                    return $http.post('/rest/messages/message', msg);
                }
            },


            /** Changes the status of an existing message */
            updateMessageStatus: function(msg, status) {
                return $http.put('/rest/messages/message/' + msg.id + '/status', status);
            },


            /** Returns the repository path to the attachment file */
            attachmentRepoPath: function (message, attachment) {
                var repoPath = message.editRepoPath || message.repoPath;
                return repoPath + '/attachments/' + encodeURIComponent(attachment.fileName);
            },

            /** Deletes the attahcment file at the given repository path */
            deleteAttachmentFile: function (repoPath) {
                return $http.delete('/rest/repo/file/' + repoPath);
            },


            /** Computes the title line for the given message */
            computeTitleLine: function (msg) {
                return $http.post('/rest/messages/compute-title-line', msg);
            },


            /** Formats the message geometry according to given template */
            formatMessageGeometry: function (geometry, lang, template, format) {
                var params = 'lang=' + lang + '&template=' + template + '&format=' + format;
                return $http.post('/rest/messages/format-message-geometry?' + params, geometry);
            },


            /** Computes the charts intersecting with the current message geometry **/
            intersectingCharts: function (featureCollection) {
                return $http.post('/rest/charts/intersecting-charts', featureCollection);
            },

            /** Returns the history of the given message */
            messageHistory: function(id) {
                return $http.get('/rest/messages/message/' + id + '/history');
            },


            /** Changes the the message map image to be the base-64 encoded png */
            changeMessageMapImage: function (repoPath, image) {
                return $http.put('/rest/message-map-image/' + repoPath, image);
            },


            /** Deletes the the message map image */
            deleteMessageMapImage: function (repoPath) {
                return $http.delete('/rest/message-map-image/' + repoPath);
            },


            /** Changes the area sort-order of a message relative to two other messages */
            changeAreaSortOrder: function (id, afterId, beforeId) {
                return $http.put('/rest/messages/change-area-sort-order', {
                    id: id,
                    afterId: afterId,
                    beforeId: beforeId
                });
            },


            /** Returns the ticket that can be used to generate PDFs (since this is via a non-ajax call */
            pdfTicket: function () {
                return $http.get('/rest/messages/pdf-ticket');
            },


            /** Returns the message tags for the current user */
            tags: function () {
                return $http.get('/rest/tags/');
            },


            /** Returns the message tags which contain the message with the given ID */
            tagsForMessage: function (messageId) {
                return $http.get('/rest/tags/message/' + messageId);
            },


            /** Adds a new message tag */
            createMessageTag: function (tag) {
                return $http.post('/rest/tags/tag/', tag);
            },


            /** Creates a temporary, short-lived message tag for the given message IDs */
            createTempMessageTag: function (messageIds) {
                return $http.post('/rest/tags/temp-tag/', messageIds);
            },


            /** Removes the messages from a message tag */
            removeMessagesFromTag: function (tag, messageIds) {
                return $http.put('/rest/tags/tag/' + encodeURIComponent(tag.tagId) + "/remove-messages", messageIds);
            },


            /** Adds the messages to a message tag */
            addMessagesToTag: function (tag, messageIds) {
                return $http.put('/rest/tags/tag/' + encodeURIComponent(tag.tagId) + "/add-messages", messageIds);
            },


            /** Adds a new message tag */
            clearMessageTag: function (tag) {
                return $http.delete('/rest/tags/tag/' + encodeURIComponent(tag.tagId) + "/messages");
            },


            /** Updates a message tag */
            updateMessageTag: function (tag) {
                return $http.put('/rest/tags/tag/' + encodeURIComponent(tag.tagId), tag);
            },


            /** Deletes a message tag */
            deleteMessageTag: function (tag) {
                return $http.delete('/rest/tags/tag/' + encodeURIComponent(tag.tagId));
            },


            /** Opens a message details dialog **/
            detailsDialog: function(messageId, messages, selection) {
                return $uibModal.open({
                    controller: "MessageDialogCtrl",
                    templateUrl: "/app/messages/message-details-dialog.html",
                    size: 'lg',
                    resolve: {
                        messageId: function () {
                            return messageId;
                        },
                        messages: function () {
                            return messages && messages.length > 0 ? extractMessageIds(messages) : [ messageId ];
                        },
                        selection: function () {
                            return selection;
                        }
                    }
                });
            },

            
            /** Opens the message tags dialog */
            messageTagsDialog: function () {
                return $uibModal.open({
                    controller: "MessageTagsDialogCtrl",
                    templateUrl: "/app/messages/message-tags-dialog.html",
                    size: 'md'
                });
            },


            /** Record the last message tag selection **/
            saveLastMessageTagSelection: function (tag) {
                if (tag) {
                    $window.sessionStorage.lastTagSelection = angular.toJson(tag);
                } else {
                    $window.sessionStorage.removeItem('lastTagSelection')
                }
            },


            /** Returns the last message tag selection - or null if undefined **/
            getLastMessageTagSelection: function () {
                if ($window.sessionStorage.lastTagSelection) {
                    try {
                        return angular.fromJson($window.sessionStorage.lastTagSelection);
                    } catch (error) {
                    }
                }
                return null;
            },


            /** Opens the message print dialog */
            messagePrintDialog: function (total) {
                return $uibModal.open({
                    controller: "MessagePrintDialogCtrl",
                    templateUrl: "/app/messages/message-print-dialog.html",
                    size: 'sm',
                    resolve: {
                        total: function () {
                            return total;
                        }
                    }
                });
            },

            /** Opens the message print dialog */
            printMessage: function (messageId) {
                var that = this;
                that.messagePrintDialog(1).result
                    .then(function (printSettings) {
                        that.pdfTicket()
                            .success(function (ticket) {

                                var params = 'lang=' + $rootScope.language + '&ticket=' + ticket;

                                if (printSettings && printSettings.pageOrientation) {
                                    params += '&pageOrientation=' + printSettings.pageOrientation;
                                }
                                if (printSettings && printSettings.pageSize) {
                                    params += '&pageSize=' + printSettings.pageSize;
                                }

                                $window.location = '/rest/messages/message/' + messageId + '.pdf?' + params;
                            });
                    });
            },


            /** Sorts the messages withing an area **/
            sortAreaMessagesDialog: function () {
                // Get the user to pick an area with a geometry
                return $uibModal.open({
                    controller: "SortAreaDialogCtrl",
                    templateUrl: "/app/messages/sort-area-dialog.html",
                    size: 'lg'
                });
            }

        };
    }])


    /**
     * Interface for calling the application server
     */
    .factory('FilterService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns the message filters */
            getFilters: function() {
                return $http.get('/rest/filters/all');
            },


            /** Adds a new message filter */
            addFilter: function(filter) {
                return $http.post('/rest/filters/', filter);
            },


            /** Removes hte message filter with the given ID */
            removeFilter: function(filterId) {
                return $http.delete('/rest/filters/' + filterId);
            }

        };
    }]);

