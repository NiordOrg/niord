
/**
 * The message list service
 */
angular.module('niord.messages')

    /**
     * Interface for calling the application server
     */
    .factory('MessageService', [ '$rootScope', '$http', '$uibModal',
        function($rootScope, $http, $uibModal) {
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


            /** Returns the ticket that can be used to generate PDFs (since this is via a non-ajax call */
            pdfTicket: function () {
                return $http.get('/rest/messages/pdf-ticket');
            },


            /** Returns the message tags for the current user */
            tags: function () {
                return $http.get('/rest/tags/');
            },

            /** Adds a new message tag */
            createMessageTag: function (tag) {
                return $http.post('/rest/tags/tag/', tag);
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
            detailsDialog: function(messageId, messages) {
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

