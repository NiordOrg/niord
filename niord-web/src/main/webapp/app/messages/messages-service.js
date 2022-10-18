
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

            /** Returns the default sort-by key **/
            defaultSortBy : function () {
                if ($rootScope.domain && $rootScope.domain.messageSortOrder) {
                    // messageSortOrder should have the format "AREA ASC"
                    var sort = $rootScope.domain.messageSortOrder.toUpperCase();
                    if (sort.startsWith('AREA')) {
                        return "AREA";
                    } else if (sort.startsWith('ID')) {
                            return "ID";
                    } else if (sort.startsWith('DATE')) {
                        return "DATE";
                    }
                }
                return "AREA";
            },


            /** Returns the default sort-order **/
            defaultSortOrder : function () {
                if ($rootScope.domain && $rootScope.domain.messageSortOrder) {
                    // messageSortOrder should have the format "AREA ASC"
                    var sort = $rootScope.domain.messageSortOrder.toUpperCase();
                    if (sort.endsWith('ASC')) {
                        return "ASC";
                    } else if (sort.endsWith('DESC')) {
                        return "DESC";
                    }
                }
                return "ASC";
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
                return $http.get('/rest/messages/message/' + encodeURIComponent(id) + '?lang=' + $rootScope.language);
            },


            /** Returns the message with the given ID in all languages */
            allLangDetails: function (id) {
                return $http.get('/rest/messages/message/' + encodeURIComponent(id));
            },


            /** Returns the editable message with the given ID */
            editableDetails: function (id) {
                return $http.get('/rest/messages/editable-message/' + encodeURIComponent(id) + '?lang=' + $rootScope.language);
            },


            /** Returns a new draft message template */
            newMessageTemplate: function (mainType, populate) {
                var params = 'mainType=' + mainType;
                if (populate) {
                    params += '&populate=true';
                }
                return $http.get('/rest/messages/new-message-template?' + params);
            },


            /** Returns a draft copy of the given message with the given reference type added to the original */
            copyMessageTemplate: function (id, referenceType) {

                var refParam = (referenceType) ? '&referenceType=' + referenceType : '';
                return $http.get('/rest/messages/copy-message-template/' + encodeURIComponent(id)
                            + '?lang=' + $rootScope.language + refParam);
            },


            /** Returns the referenced messages, optionally with the given types and status **/
            referencedMessages: function (id, referenceTypes, status) {
                var refParam = '';
                if (referenceTypes && referenceTypes.length > 0) {
                    angular.forEach(referenceTypes, function (referenceType) {
                        refParam +='&referenceType=' + referenceType
                    });
                }
                var statusParam = (status) ? '&status=' + status : '';
                return $http.get('/rest/messages/referenced-messages/' + encodeURIComponent(id)
                    + '?lang=' + $rootScope.language + refParam + statusParam);
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
            updateMessageStatus: function(msg, status, params) {
                params = params || '';
                return $http.put('/rest/messages/message/' + msg.id + '/status?' + params, status);
            },


            /** Changes the statuses of a list of messages message */
            updateMessageStatuses: function(updates) {
                return $http.put('/rest/messages/update-statuses', updates);
            },


            /** Returns the repository path for uploading an attachment file */
            attachmentUploadRepoPath: function (message) {
                return '/rest/messages/attachments/' + message.editRepoPath + '/' + message.revision;
            },

            /** Returns the repository path to the attachment file */
            attachmentEditRepoPath: function (message, attachment) {
                return message.editRepoPath + '/' + message.revision + '/' + encodeURIComponent(attachment.fileName);
            },

            /** Deletes the attahcment file at the given repository path */
            deleteAttachmentFile: function (repoPath) {
                return $http['delete']('/rest/repo/file/' + repoPath);
            },


            /** Computes the editor fields and the title line for the given message */
            adjustEditableMessage: function (msg) {
                return $http.post('/rest/messages/adjust-editable-message', msg);
            },


            /** Extracts the message publication from the message */
            extractMessagePublication: function (msg, publicationId, lang) {
                lang = lang || $rootScope.language;
                var params = 'lang=' + lang + '&publicationId=' + encodeURIComponent(publicationId);
                return $http.post('/rest/messages/extract-message-publication?' + params, msg);
            },


            /** Updates the message publications with the given parameters and link */
            updateMessagePublications: function (msg, publicationId, parameters, link, lang) {
                var params = 'publicationId=' + encodeURIComponent(publicationId);
                if (parameters) {
                    params += '&parameters=' + encodeURIComponent(parameters);
                }
                if (link) {
                    params += '&link=' + encodeURIComponent(link);
                }
                if (lang) {
                    params += '&lang=' + encodeURIComponent(lang);
                }
                return $http.post('/rest/messages/update-message-publications?' + params, msg);
            },


            /** Formats the message geometry according to given template */
            formatMessageGeometry: function (geometry, lang, template, format) {
                var params = 'lang=' + lang + '&template=' + template + '&format=' + format;
                return $http.post('/rest/messages/format-message-geometry?' + params, geometry);
            },


            /** Computes the areas intersecting with the current message geometry **/
            intersectingAreas: function (featureCollection, maxLevel) {
                maxLevel = maxLevel || 2;
                return $http.post('/rest/areas/intersecting-areas?maxLevel=' + maxLevel
                                + '&lang=' + $rootScope.language + '&domain=true', featureCollection);
            },


            /** Computes the charts intersecting with the current message geometry **/
            intersectingCharts: function (featureCollection) {
                return $http.post('/rest/charts/intersecting-charts', featureCollection);
            },


            /** Returns the history of the given message */
            messageHistory: function(id) {
                return $http.get('/rest/messages/message/' + id + '/history');
            },


            /** Returns all publications */
            searchPublications: function(title, messagePublication, mainType, type, status, details) {
                var params = 'lang=' + $rootScope.language;
                if (title) {
                    params += '&title=' + encodeURIComponent(title);
                }
                if (messagePublication) {
                    params += '&messagePublication=' + messagePublication;
                }
                if (mainType) {
                    params += '&mainType=' + mainType;
                }
                if (type) {
                    params += '&type=' + type;
                }
                if (status) {
                    angular.forEach(status.split(','), function (s) {
                        params += '&status=' + s;
                    });
                }
                var endpoint = (details) ? 'search-details' : 'search';
                return $http.get('/rest/publications/' + endpoint + '?' + params);
            },


            /** Returns the details for the given publication **/
            getPublicationDetails: function (publicationId) {
                return $http.get('/rest/publications/publication/' + publicationId);
            },


            /** Returns all sources **/
            getSources: function () {
                return $http.get('/rest/sources/search?inactive=false&lang=' + $rootScope.language);
            },


            /** Returns the details for the given sources **/
            getSourceDetails: function (sources) {
                var ids = [];
                angular.forEach(sources, function (s) { ids.push(s.id); });

                return $http.get('/rest/sources/search/' + ids.join(','));
            },


            /** Returns the recently edited draft messages */
            recentlyEditedDrafts: function(maxMessageNo) {
                maxMessageNo = maxMessageNo || 20;
                return $http.get('/rest/messages/recently-edited-drafts?lang=' + $rootScope.language
                                    + '&maxMessageNo=' + maxMessageNo);
            },


            /** Returns the comments for the given message */
            comments: function(id) {
                return $http.get('/rest/messages/message/' + id + '/comments');
            },


            /** Creates a comment for the given message */
            createComment: function(id, comment) {
                return $http.post('/rest/messages/message/' + id + '/comment?lang=' + $rootScope.language, comment);
            },


            /** Updates a comment for the given message */
            updateComment: function(id, comment) {
                return $http.put('/rest/messages/message/' + id + '/comment/' + comment.id
                    + '?lang=' + $rootScope.language, comment);
            },


            /** Acknowledges a comment for the given message */
            acknowledgeComment: function(id, comment) {
                return $http.put('/rest/messages/message/' + id + '/comment/' + comment.id + '/ack');
            },


            /** Changes the custom message map image to be the base-64 encoded png */
            changeMessageMapImage: function (repoPath, image) {
                return $http.put('/rest/message-map-image/' + repoPath, image);
            },


            /** Changes the area sort-order of a message relative to two other messages */
            changeAreaSortOrder: function (id, afterId, beforeId) {
                return $http.put('/rest/messages/change-area-sort-order', {
                    id: id,
                    afterId: afterId,
                    beforeId: beforeId
                });
            },


            /** Returns the ticket that can be used to generate PDFs or export archives (via a non-ajax call */
            authTicket: function () {
                return $http.get('/rest/tickets/ticket');
            },


            /** Returns the message tags for the current user */
            tags: function (params) {
                params = params || '';
                return $http.get('/rest/tags/search?' + params);
            },


            /** Returns the message tags which contain the message with the given ID */
            tagsForMessage: function (messageId, includeLocked) {
                var params = (includeLocked === true) ? '' : '?locked=false';
                return $http.get('/rest/tags/message/' + messageId + params);
            },


            /** Adds a new message tag */
            createMessageTag: function (tag) {
                return $http.post('/rest/tags/tag/', tag);
            },


            /** Creates a temporary, short-lived message tag for the given message IDs */
            createTempMessageTag: function (messageIds, ttl) {
                var params = ttl ? '?ttl=' + ttl : '';
                return $http.post('/rest/tags/temp-tag/' + params, messageIds);
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
                return $http['delete']('/rest/tags/tag/' + encodeURIComponent(tag.tagId) + "/messages");
            },


            /** Updates a message tag */
            updateMessageTag: function (tag) {
                return $http.put('/rest/tags/tag/' + encodeURIComponent(tag.tagId), tag);
            },


            /** Deletes a message tag */
            deleteMessageTag: function (tag) {
                return $http['delete']('/rest/tags/tag/' + encodeURIComponent(tag.tagId));
            },


            /** Unlocks the given message tag */
            unlockMessageTag: function (tag) {
                return $http.put('/rest/tags/unlock-tag/' + encodeURIComponent(tag.tagId));
            },


            /** Returns the given promulgation type **/
            getPromulgationType: function (typeId) {
                return $http.get('/rest/promulgations/promulgation-type/' + encodeURIComponent(typeId));
            },


            /** Called with a promulgation-specific endpoint to generate a promulgation **/
            generatePromulgation: function (type, message) {
                return $http.post('/rest/promulgations/generate/' + encodeURIComponent(type), message);
            },


            /** Returns the list of message features **/
            getMessageFeatures: function (message, excludeAffectedAreas) {
                var features = [];
                if (message && message.parts && message.parts.length > 0) {
                    angular.forEach(message.parts, function (part) {
                        if (part.geometry && part.geometry.features.length > 0) {
                            angular.forEach(part.geometry.features, function (f) {
                                if (excludeAffectedAreas && f.properties && f.properties['restriction'] === 'affected') {
                                    return;
                                }
                                features.push(f);
                            });
                        }
                    });
                }
                return features;
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
            messageTagsDialog: function (includeLocked) {
                return $uibModal.open({
                    controller: "MessageTagsDialogCtrl",
                    templateUrl: "/app/messages/message-tags-dialog.html",
                    size: 'md',
                    resolve: {
                        includeLocked: function () { return includeLocked; }
                    }
                });
            },


            /** Opens the message source dialog */
            messagePublicationsDialog: function (message, messagePublication, publicationId, lang) {
                return $uibModal.open({
                    templateUrl: '/app/editor/format-publications-dialog.html',
                    controller: 'MessagePublicationsDialogCtrl',
                    size: 'md',
                    resolve: {
                        message: function () { return message },
                        messagePublication: function () { return messagePublication },
                        publicationId: function () { return publicationId },
                        lang: function () { return lang; }
                    }
                });
            },


            /** Opens the message source dialog */
            messageSourceDialog: function () {
                return $uibModal.open({
                    controller: "MessageSourceDialogCtrl",
                    templateUrl: "/app/editor/message-source-dialog.html",
                    size: 'md'
                });
            },


            /** Record the last message tag selection **/
            saveLastMessageTagSelection: function (tag) {
                if (tag && !tag.locked) {
                    $window.sessionStorage.lastTagSelection = angular.toJson(tag);
                    $window.sessionStorage.lastTagDomain = $rootScope.domain ? $rootScope.domain.domainId : '';
                } else {
                    $window.sessionStorage.removeItem('lastTagSelection')
                }
            },


            /** Returns the last message tag selection - or null if undefined **/
            getLastMessageTagSelection: function () {
                if ($window.sessionStorage.lastTagSelection) {
                    try {
                        var lastTagSelection = angular.fromJson($window.sessionStorage.lastTagSelection);

                        // If we have changed domain, return null
                        var lastTagDomain = $window.sessionStorage.lastTagDomain;
                        if ($rootScope.domain === undefined || $rootScope.domain.domainId !== lastTagDomain) {
                            return null;
                        }
                        return lastTagSelection;
                    } catch (error) {
                    }
                }
                return null;
            },


            /** Returns the message print reports */
            printReports: function (list, expandParams) {
                var endpoint = (list) ? 'reports' : 'detail-reports';
                var params = (expandParams !== undefined) ? '?expandParams=' + expandParams : '';
                return $http.get('/rest/message-reports/' + endpoint + params);
            },


            /** Opens the message print dialog */
            messagePrintDialog: function (total, list, report) {
                return $uibModal.open({
                    controller: "MessagePrintDialogCtrl",
                    templateUrl: "/app/messages/message-print-dialog.html",
                    size: 'sm',
                    resolve: {
                        total: function () { return total; },
                        list: function () { return list; },
                        report: function () { return report; }
                    }
                });
            },

            /** Opens the message print dialog */
            printMessage: function (messageId) {
                var that = this;
                that.messagePrintDialog(1, false).result
                    .then(function (printParams) {
                        $window.location = '/rest/message-reports/message/' + messageId + '.pdf?' + printParams;
                    });
            },


            /** Compares two messages **/
            compareMessagesDialog: function (messageId1, messageId2) {
                return $uibModal.open({
                    controller: "MessageComparisonDialogCtrl",
                    templateUrl: "/app/editor/message-comparison-dialog.html",
                    size: 'lg',
                    resolve: {
                        messageId1: function () { return messageId1; },
                        messageId2: function () { return messageId2; }
                    }
                });
            },


            /** Opens the message send-mail dialog */
            messageMailDialog: function (messageIds) {
                return $uibModal.open({
                    controller: "MessageMailDialogCtrl",
                    templateUrl: "/app/messages/message-mail-dialog.html",
                    size: 'md',
                    resolve: {
                        messageIds: function () { return messageIds; }
                    }
                });
            },


            /** Sends a message e-mail based on the given parameters */
            sendMessageMail: function (params) {
                return $http.get('/rest/message-mail/send?' + params);
            },


            /** Opens the mailing list report dialog **/
            mailingListReportDialog: function() {
                return $uibModal.open({
                    controller: "MailingListReportDialogCtrl",
                    templateUrl: "/app/admin/mailing-list-reports-dialog.html",
                    size: 'lg'
                });
            },


            /** Sorts the messages withing an area **/
            sortAreaMessagesDialog: function (area, status, tag) {
                // Get the user to pick an area with a geometry
                return $uibModal.open({
                    controller: "SortAreaDialogCtrl",
                    templateUrl: "/app/messages/sort-area-dialog.html",
                    size: 'lg',
                    resolve: {
                        area: function () { return area; },
                        status: function () { return status; },
                        tag: function () { return tag; }
                    }
                });
            },


            /** Imports messages from a zip archive **/
            importMessagesDialog: function () {
                return $uibModal.open({
                    controller: "ImportMessagesDialogCtrl",
                    templateUrl: "/app/messages/message-import-dialog.html",
                    size: 'md'
                });
            },


            /** Bulk-updates the status of the message selection **/
            updateStatusDialog: function (selection) {
                return $uibModal.open({
                    controller: "UpdateStatusDialogCtrl",
                    templateUrl: "/app/messages/update-status-dialog.html",
                    size: 'lg',
                    resolve: {
                        selection: function () { return selection; }
                    }
                });
            },


            /** Copies the message **/
            copyMessageDialog: function (messageId, mainType, dismissAction) {

                $uibModal.open({
                    templateUrl: "/app/messages/copy-message-dialog.html",
                    size: 'sm',
                    controller: function($scope) {
                        $scope.mainType = mainType;
                        $scope.data = {
                            includeReference: false
                        };
                    }
                }).result.then(function (includeReference) {
                    var referenceType = includeReference ? 'REFERENCE' : '';
                    // Navigate to the message editor page
                    $state.go(
                        'editor.copy',
                        { id: messageId,  referenceType : referenceType },
                        { reload: true }
                    );
                    if (dismissAction) {
                        dismissAction();
                    }
                });
            }

        };
    }])


    /**
     * Interface for calling the application server.
     * <p>
     * Keep function in sync with DateIntervalDirective.java
     */
    .factory('DateIntervalService', [ '$translate',
        function($translate) {
        'use strict';

        return {

            /** Translates a date interval */
            translateDateInterval: function(lang, di, tz) {

                var from = di && di.fromDate ? moment(di.fromDate).locale(lang) : undefined;
                var to = di && di.toDate ? moment(di.toDate).locale(lang) : undefined;

                // TODO: Optimize based on same month and year. E.g.:
                // "3 May 2016 - 4 Jun 2016" -> "3 May - 4 Jun 2016"
                // "3 May 2016 - 4 May 2016" -> "3 - 4 May 2016"

                if (di && from !== undefined && to !== undefined) {
                    var fromDateTxt = from.format("ll");
                    var toDateTxt = to.format("ll");
                    var time = '';

                    if (fromDateTxt === toDateTxt) {
                        if (di.allDay) {
                            // Same dates
                            time = fromDateTxt;
                        } else {
                            var fromTimeTxt = from.format("lll");
                            var toTimeTxt = to.format("lll");
                            if (fromTimeTxt === toTimeTxt) {
                                // Same date-time
                                time = fromTimeTxt;
                            } else {
                                // same date, different time
                                time = fromDateTxt + ", "
                                    + from.format("LT")
                                    + " - "
                                    + to.format("LT");
                            }
                        }

                    } else {
                        if (di.allDay) {
                            // Different dates
                            time = fromDateTxt + " - " + toDateTxt;
                        } else {
                            // Different dates
                            var fromTimeTxt = from.format("lll");
                            var toTimeTxt = to.format("lll");
                            time = fromTimeTxt + " - " + toTimeTxt;
                        }
                    }

                    // Add time zone
                    if (tz) {
                        time = time + from.format(' z');
                    }
                    return time;

                } else if (di && from !== undefined) {
                    var format = di.allDay ? 'll' : 'lll';
                    format += (tz) ? ' z' : '';
                    var fromTxt = from.format(format);
                    return $translate.instant('msg.time.from_date', { fromDate : fromTxt }, null, lang);

                } else if (di && to !== undefined) {
                    var format = di.allDay ? 'll' : 'lll';
                    format += (tz) ? ' z' : '';
                    var toTxt = to.format(format);
                    return $translate.instant('msg.time.to_date', { toDate : toTxt }, null, lang);

                } else {
                    return $translate.instant('msg.time.until_further_notice', {}, null, lang);
                }
            },

            /** Translates a time interval */
            translateTimeInterval: function(lang, di) {
                var from = di && di.fromDate ? moment(di.fromDate).locale(lang) : undefined;
                var to = di && di.toDate ? moment(di.toDate).locale(lang) : undefined;

                var time = '';
                if (from && to) {
                    time = from.format('LT') + ' - ' + to.format('LT z');
                } else if (from) {
                    time = $translate.instant('msg.time.from_date', { fromDate : from.format('LT z') }, null, lang);
                } else if (to) {
                    time = $translate.instant('msg.time.to_date', { toDate : to.format('LT z') }, null, lang);
                }

                return time;
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
                return $http.post('/rest/filters/filter/', filter);
            },


            /** Removes hte message filter with the given ID */
            removeFilter: function(filterId) {
                return $http['delete']('/rest/filters/filter/' + filterId);
            }

        };
    }]);
