
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
 * The message list controller
 *
 * Whenever the message list filter and map settings are updated, the corresponding request parameters
 * are updated. This allows e.g. for bookmarking of the current state.
 */
angular.module('niord.messages')
    .controller('MessageListCtrl', ['$scope', '$rootScope', '$window', '$location', '$http', '$timeout', 'growl',
                'AuthService', 'FilterService', 'MessageService', 'AtonService',
        function ($scope, $rootScope, $window, $location, $http, $timeout, growl,
                  AuthService, FilterService, MessageService, AtonService) {
            'use strict';

            var loadTimer;

            $scope.isEditor = $rootScope.hasRole('editor');
            $scope.loggedIn = AuthService.loggedIn;
            $scope.page = 0;
            $scope.maxSize = 100;
            $scope.searchDomain = $rootScope.domain;
            $scope.showFilter = true;
            $scope.messageList = [];
            $scope.selection = $rootScope.messageSelection;
            $scope.selectionList = []; // Flattened list of selected messages
            $scope.totalMessageNo = 0;
            $scope.filterNames = [ 'domain', 'messageSeries', 'text', 'type', 'status', 'tag', 'publication',
                'user', 'comments', 'reference', 'chart', 'area', 'category', 'date' ];
            $scope.state = {

                /** Sorting **/
                sortBy : MessageService.defaultSortBy(),
                sortOrder : MessageService.defaultSortOrder(),

                /** Map state. Also serves as a mandatory filter in map mode **/
                map : {
                    enabled: true,
                    reloadMap : false // can be used to trigger a map reload
                },
                domain : {
                    enabled: false,
                    domain: undefined
                },
                text: {
                    enabled: false,
                    focusField: '#query',
                    query: ''
                },
                type: {
                    enabled: false,
                    mainType: '',
                    PERMANENT_NOTICE: false,
                    TEMPORARY_NOTICE: false,
                    PRELIMINARY_NOTICE: false,
                    MISCELLANEOUS_NOTICE: false,
                    COASTAL_WARNING: false,
                    SUBAREA_WARNING: false,
                    NAVAREA_WARNING: false,
                    LOCAL_WARNING: false
                },
                status: {
                    enabled: false,
                    PUBLISHED: false,
                    DRAFT: false,
                    VERIFIED: false,
                    CANCELLED: false,
                    EXPIRED: false,
                    DELETED: false
                },
                tag: {
                    enabled: false,
                    focusField: '#tags input.ui-select-search',
                    tags: []
                },
                publication: {
                    enabled: false,
                    focusField: '#publications input.ui-select-search',
                    publications: []
                },
                user: {
                    enabled: false,
                    username: undefined,
                    userType: undefined
                },
                comments: {
                    enabled: false,
                    comments: ''
                },
                reference: {
                    enabled: false,
                    messageId: undefined,
                    referenceLevels: 1
                },
                aton: {
                    enabled: false,
                    focusField: '#atons > div > input.ui-select-search',
                    atons: []
                },
                chart: {
                    enabled: false,
                    focusField: '#charts input.ui-select-search',
                    charts: []
                },
                messageSeries: {
                    enabled: false,
                    focusField: '#messageSeries input.ui-select-search',
                    messageSeries: []
                },
                area: {
                    enabled: false,
                    focusField: '#areas input.ui-select-search',
                    areas: []
                },
                category: {
                    enabled: false,
                    focusField: '#categories input.ui-select-search',
                    categories: []
                },
                date: {
                    enabled: false,
                    dateType: 'PUBLISH_DATE',
                    fromDate: undefined,
                    toDate: undefined
                }
            };

            // Used for initializing base data filter fields from the initial request parameters.
            // The fields initialized with these IDs will reset the arrays, once they have
            // loaded the corresponding entities and updated the "state" object with the them.
            $scope.initTagIds = [];
            $scope.initPublicationIds = [];
            $scope.initAreaIds = [];
            $scope.initCategoryIds = [];
            $scope.initChartIds = [];
            $scope.initSeriesIds = [];


            /** Returns the number of pending base entities that has not yet been loaded */
            $scope.pendingInitDataNo = function () {
                return $scope.initTagIds.length + $scope.initPublicationIds.length + $scope.initAreaIds.length
                    + $scope.initCategoryIds.length + $scope.initChartIds.length + $scope.initSeriesIds.length;
            };


            /** Destroy any pending message loading operations **/
            $scope.$on('$destroy', function() {
                if (angular.isDefined(loadTimer)) {
                    $timeout.cancel(loadTimer);
                    loadTimer = undefined;
                }
            });


            /** Returns if the domain used for searching messages only allows for public messages **/
            $scope.searchPublicMessages = function () {
                return !$rootScope.hasRole('editor') || $rootScope.domain != $scope.searchDomain;
            };


            /*****************************/
            /** Filter Handling         **/
            /*****************************/

            /** Enables or disables the given filter **/
            $scope.enableFilter = function (name, enabled) {
                var filter = $scope.state[name];
                filter.enabled = enabled;
                if (!enabled) {
                    $scope.clearFilter(name);
                } else if (filter.focusField) {
                    $timeout(function () { $(filter.focusField).focus() });
                }
            };


            /** Returns if the filter is enabled **/
            $scope.filterEnabled = function (name) {
                var filter = $scope.state[name];
                return filter && filter.enabled;
            };


            /** Updates all values of the status filter to have the given selected state **/
            $scope.updateStateFilter = function (selected) {
                var status = $scope.state.status;
                status.PUBLISHED = selected;
                status.DRAFT = selected;
                status.VERIFIED = selected;
                status.CANCELLED = selected;
                status.EXPIRED = selected;
                status.DELETED = selected;
            };


            /** Updates all values of the type filter to have the given selected state **/
            $scope.updateTypeFilter = function (mainType, selected) {
                var type = $scope.state.type;
                if (mainType == 'NW') {
                    type.COASTAL_WARNING = selected;
                    type.SUBAREA_WARNING = selected;
                    type.NAVAREA_WARNING = selected;
                    type.LOCAL_WARNING = selected;
                } else if (mainType == 'NM') {
                    type.PERMANENT_NOTICE = selected;
                    type.TEMPORARY_NOTICE = selected;
                    type.PRELIMINARY_NOTICE = selected;
                    type.MISCELLANEOUS_NOTICE = selected;
                }
            };


            /** Clears the given filter **/
            $scope.clearFilter = function (name) {
                var filter = $scope.state[name];
                filter.enabled = false;
                switch (name) {
                    case 'text':
                        filter.query = '';
                        break;
                    case 'type':
                        filter.mainType = '';
                        $scope.updateTypeFilter('NW', false);
                        $scope.updateTypeFilter('NM', false);
                        break;
                    case 'status':
                        $scope.updateStateFilter(false);
                        break;
                    case 'tag':
                        filter.tags.length = 0;
                        break;
                    case 'publication':
                        filter.publications.length = 0;
                        break;
                    case 'user':
                        filter.username = undefined;
                        filter.userType = undefined;
                        break;
                    case 'comments':
                        filter.comments = '';
                        break;
                    case 'reference':
                        filter.messageId = undefined;
                        filter.referenceLevels = 1;
                        break;
                    case 'aton':
                        filter.atons = [];
                        break;
                    case 'chart':
                        filter.charts.length = 0;
                        break;
                    case 'domain':
                        filter.domain = undefined;
                        break;
                    case 'messageSeries':
                        filter.messageSeries.length = 0;
                        break;
                    case 'area':
                        filter.areas.length = 0;
                        break;
                    case 'category':
                        filter.categories.length = 0;
                        break;
                    case 'date':
                        filter.dateType = 'PUBLISH_DATE';
                        filter.fromDate = undefined;
                        filter.toDate = undefined;
                        break;
                }
            };


            /** Clears all filters **/
            $scope.clearAllFilters = function () {
                angular.forEach($scope.filterNames, function (name) {
                    $scope.clearFilter(name);
                })
            };


            /** Converts the filters into a request parameter string **/
            $scope.toRequestFilterParameters = function () {

                var params = '';
                var s = $scope.state;

                // Handle map
                if (s.map.enabled) {
                    if (s.map.zoom) {
                        params += '&zoom=' + s.map.zoom;
                    }
                    if (s.map.center && s.map.center.length == 2) {
                        params += '&lon=' + s.map.center[0] + '&lat=' + s.map.center[1];
                    }
                    if (s.map.extent && s.map.extent.length == 4) {
                        params += '&minLon=' + s.map.extent[0] + '&minLat=' + s.map.extent[1] +
                            '&maxLon=' + s.map.extent[2] + '&maxLat=' + s.map.extent[3];
                    }
                }

                // Handle Filters
                if (s.domain.enabled && s.domain.domain) {
                    params += '&domain=' + encodeURIComponent(s.domain.domain.domainId);
                }
                if (s.text.enabled) {
                    params += '&query=' + encodeURIComponent(s.text.query);
                }
                if (s.type.enabled) {
                    if (s.type.mainType) {
                        params += '&mainType=' + s.type.mainType;
                    }
                    if (s.type.PERMANENT_NOTICE) {
                        params += '&type=PERMANENT_NOTICE';
                    }
                    if (s.type.TEMPORARY_NOTICE) {
                        params += '&type=TEMPORARY_NOTICE';
                    }
                    if (s.type.PRELIMINARY_NOTICE) {
                        params += '&type=PRELIMINARY_NOTICE';
                    }
                    if (s.type.MISCELLANEOUS_NOTICE) {
                        params += '&type=MISCELLANEOUS_NOTICE';
                    }
                    if (s.type.COASTAL_WARNING) {
                        params += '&type=COASTAL_WARNING';
                    }
                    if (s.type.SUBAREA_WARNING) {
                        params += '&type=SUBAREA_WARNING';
                    }
                    if (s.type.NAVAREA_WARNING) {
                        params += '&type=NAVAREA_WARNING';
                    }
                    if (s.type.LOCAL_WARNING) {
                        params += '&type=LOCAL_WARNING';
                    }
                }
                if (s.status.enabled) {
                    if (s.status.PUBLISHED) {
                        params += '&status=PUBLISHED';
                    }
                    if (s.status.DRAFT) {
                        params += '&status=DRAFT';
                    }
                    if (s.status.VERIFIED) {
                        params += '&status=VERIFIED';
                    }
                    if (s.status.CANCELLED) {
                        params += '&status=CANCELLED';
                    }
                    if (s.status.EXPIRED) {
                        params += '&status=EXPIRED';
                    }
                    if (s.status.DELETED) {
                        params += '&status=DELETED';
                    }
                }
                if (s.tag.enabled) {
                    angular.forEach(s.tag.tags, function (tag) {
                        params += '&tag=' + tag.tagId;
                    })
                }
                if (s.publication.enabled) {
                    angular.forEach(s.publication.publications, function (publication) {
                        params += '&publication=' + publication.publicationId;
                    })
                }
                if ($scope.loggedIn && s.user.enabled && s.user.username && s.user.username.length > 0) {
                    params += '&username=' + encodeURIComponent(s.user.username);
                    if (s.user.userType && s.user.userType.length > 0) {
                        params += '&userType=' + s.user.userType;
                    }
                }
                if (s.comments.enabled && s.comments.comments.length > 0) {
                    params += '&comments=' + s.comments.comments;
                }
                if (s.reference.enabled && s.reference.messageId) {
                    params += '&messageId=' + encodeURIComponent(s.reference.messageId)
                            + '&referenceLevels=' + s.reference.referenceLevels;
                }
                if (s.aton.enabled) {
                    angular.forEach(s.aton.atons, function (aton) {
                        params += '&aton=' + AtonService.getAtonUid(aton);
                    })
                }
                if (s.chart.enabled) {
                    angular.forEach(s.chart.charts, function (chart) {
                        params += '&chart=' + chart.chartNumber;
                    })
                }
                if (s.messageSeries.enabled) {
                    angular.forEach(s.messageSeries.messageSeries, function (s) {
                        params += '&messageSeries=' + s.seriesId;
                    })
                }
                if (s.area.enabled) {
                    angular.forEach(s.area.areas, function (area) {
                        params += '&area=' + area.id;
                    })
                }
                if (s.category.enabled) {
                    angular.forEach(s.category.categories, function (category) {
                        params += '&category=' + category.id;
                    })
                }
                if (s.date.enabled) {
                    if (s.date.fromDate) {
                        params += '&fromDate=' + s.date.fromDate;
                    }
                    if (s.date.toDate) {
                        params += '&toDate=' + s.date.toDate;
                    }
                    if (s.date.fromDate || s.date.toDate) {
                        params += '&dateType=' + s.date.dateType;
                    }
                }

                // Add sorting
                if ($scope.state.sortBy != MessageService.defaultSortBy()) {
                    params += '&sortBy=' + $scope.state.sortBy;
                }
                if ($scope.state.sortOrder != MessageService.defaultSortOrder()) {
                    params += '&sortOrder=' + $scope.state.sortOrder;
                }

                // Skip first '&'
                return params.length > 0 ? params.substr(1) : '';
            };


            /** Parses the request parameters into a filter **/
            $scope.readRequestFilterParameters = function () {

                var params = $location.search();
                var s = $scope.state;

                // Handle map
                if (params.zoom) {
                    s.map.zoom = parseInt(params.zoom);
                }
                if (params.lat && params.lon) {
                    s.map.center = [parseFloat(params.lon), parseFloat(params.lat)];
                }
                if (params.minLon && params.minLat && params.maxLon && params.maxLat) {
                    s.map.extent = [params.minLon, params.minLat, params.maxLon, params.maxLat];
                }

                // Handle filters
                if (params.domain) {
                    var domains = $.grep($rootScope.domains, function (domain) {
                        return domain.domainId == params.domain;
                    });
                    s.domain.enabled = domains.length == 1;
                    s.domain.domain = domains.length == 1 ? domains[0] : undefined;
                }
                if (params.query) {
                    s.text.enabled = true;
                    s.text.query = params.query;
                }
                if (params.mainType) {
                    s.type.enabled = true;
                    s.type.mainType = params.mainType;
                }
                if (params.type) {
                    s.type.enabled = true;
                    var types = (typeof params.type === 'string') ? [ params.type ] : params.type;
                    angular.forEach(types, function (type) {
                        s.type[type] = true;
                    });
                }
                if (params.status && params.status.length > 0) {
                    s.status.enabled = true;
                    var statuses = (typeof params.status === 'string') ? [ params.status ] : params.status;
                    angular.forEach(statuses, function (status) {
                        s.status[status] = true;
                    });
                }
                if (params.tag && params.tag.length > 0) {
                    s.tag.enabled = true;
                    $scope.initTagIds = (typeof params.tag === 'string') ? [ params.tag ] : params.tag;
                }
                if (params.publication && params.publication.length > 0) {
                    s.publication.enabled = true;
                    $scope.initPublicationIds = (typeof params.publication === 'string') ? [ params.publication ] : params.publication;
                }
                if ($scope.loggedIn && params.username) {
                    s.user.enabled = true;
                    s.user.username = params.username;
                    s.user.userType = params.userType ? params.userType : '';
                }
                if (params.comments) {
                    s.comments.enabled = true;
                    s.comments.comments = params.comments;
                }
                if (params.messageId) {
                    s.reference.enabled = true;
                    s.reference.messageId = params.messageId;
                    s.reference.referenceLevels = (params.referenceLevels) ? parseInt(params.referenceLevels) : 1;
                }
                if (params.aton && params.aton.length > 0) {
                    s.aton.enabled = true;
                    var atons = (typeof params.aton === 'string') ? params.aton : params.aton.join();
                    $http.get('/rest/atons/aton/' + atons + '?lang=' + $rootScope.language)
                        .then(function(response) {
                            s.aton.atons = response.data;
                        });
                }
                if (params.chart && params.chart.length > 0) {
                    s.chart.enabled = true;
                    $scope.initChartIds = (typeof params.chart === 'string') ? [ params.chart ] : params.chart;
                }
                if (params.messageSeries && params.messageSeries.length > 0) {
                    s.messageSeries.enabled = true;
                    $scope.initSeriesIds = (typeof params.messageSeries === 'string') ? [ params.messageSeries ] : params.messageSeries;
                }
                if (params.area && params.area.length > 0) {
                    s.area.enabled = true;
                    $scope.initAreaIds = (typeof params.area === 'string') ? [ params.area ] : params.area;
                }
                if (params.category && params.category.length > 0) {
                    s.category.enabled = true;
                    $scope.initCategoryIds = (typeof params.category === 'string') ? [ params.category ] : params.category;
                }
                if (params.fromDate || params.toDate) {
                    s.date.enabled = true;
                    s.date.fromDate = params.fromDate ? parseInt(params.fromDate) : undefined;
                    s.date.toDate = params.toDate ? parseInt(params.toDate) : undefined;
                    if (params.dateType) {
                        s.date.dateType = params.dateType;
                    }
                }
                if (params.sortBy) {
                    s.sortBy = params.sortBy;
                }
                if (params.sortOrder) {
                    s.sortOrder = params.sortOrder;
                }
            };


            /** Called when the filter is updated **/
            $scope.filterUpdated = function () {
                // When the page is first loaded, the base entities specified by parameters (e.g. tags, areas, etc)
                // may not have been loaded yet by the respective filter fields, and thus, the state not yet updated.
                if ($scope.pendingInitDataNo() > 0) {
                    return;
                }

                // Only allow user searches if the user is logged in
                if (!$scope.loggedIn) {
                    $scope.state.user.username = undefined;
                    $scope.state.user.userType = undefined;
                }

                // Enforce validity of the filter selection
                if ($scope.state.type.mainType == 'NW') {
                    $scope.updateTypeFilter('NM', false);
                } else if ($scope.state.type.mainType == 'NM') {
                    $scope.updateTypeFilter('NW', false);
                }

                // Update the current search domain
                $scope.searchDomain = $scope.state.domain.domain && $scope.state.domain.enabled
                    ? $scope.state.domain.domain
                    : $rootScope.domain;

                if ($scope.searchPublicMessages()) {
                    $scope.state.status.DRAFT = false;
                    $scope.state.status.VERIFIED = false;
                    $scope.state.status.DELETED = false;
                }

                if (loadTimer) {
                    $timeout.cancel(loadTimer);
                }
                $scope.page = 0;
                loadTimer = $timeout($scope.refreshMessages, 100);
            };


            // Use for AtoN selection
            $scope.atons = [];
            $scope.refreshAtons = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/atons/search-name?name=' + encodeURIComponent(name) + '&lang=' + $rootScope.language + '&maxAtonNo=10'
                ).then(function(response) {
                    $scope.atons = response.data;
                });
            };


            /*****************************/
            /** Selection Handling      **/
            /*****************************/

            /** Returns if the given message is selected or not **/
            $scope.isSelected = function (message) {
                return $scope.selection.get(message.id) !== undefined;
            };


            /** Toggle the selection state of the message **/
            $scope.toggleSelectMessage = function (message) {
                if ($scope.isSelected(message)) {
                    $scope.selection.remove(message.id);
                } else {
                    $scope.selection.put(message.id, angular.copy(message));
                }
            };

            /** Clears the message selection **/
            $scope.clearSelection = function () {
                $scope.selection.clear();
            };


            /** Selects all messages **/
            $scope.selectAll = function () {
                angular.forEach($scope.messageList, function (message) {
                    if (!$scope.isSelected(message)) {
                        $scope.selection.put(message.id, angular.copy(message));
                    }
                })
            };


            /** Toggles between selecting all or none of the messages **/
            $scope.toggleSelectAll = function () {
                if ($scope.selection.size() > 0) {
                    $scope.clearSelection();
                } else {
                    $scope.selectAll();
                }
            };


            // Whenever the selection changes, push all selected messages into the "selectionList" array
            $scope.$watchCollection("selection.keys", function () {
                $scope.selectionList.length = 0;
                $scope.selectionList.push.apply($scope.selectionList, $scope.selection.values());
            });

            /*****************************/
            /** Named Filters Handling  **/
            /*****************************/

            $scope.namedFilters = [];

            /** Loads the persisted names filters **/
            $scope.loadNamedFilters = function () {
                if ($scope.loggedIn) {
                    FilterService
                        .getFilters()
                        .success(function (filters) { $scope.namedFilters = filters; });
                }
            };

            /** Saves the current filter as a named filter **/
            $scope.saveNamedFilter = function () {
                var name = prompt("Please enter filter name");
                if (name) {
                    var filter = {
                        name: name,
                        parameters: $scope.toRequestFilterParameters()
                    };
                    FilterService
                        .addFilter(filter)
                        .success($scope.loadNamedFilters);
                }
            };


            /** Removes the given persisted named filter **/
            $scope.removeNamedFilter = function (filter) {
                FilterService
                    .removeFilter(filter.id)
                    .success($scope.loadNamedFilters);
            };


            /** Sets the given named filter as the current filter **/
            $scope.applyNamedFilter = function (filter) {
                $scope.clearAllFilters();
                $location.search(filter.parameters);
                $scope.readRequestFilterParameters();
                $scope.state.map.reloadMap = true;
            };


            /*****************************/
            /** Message Tag functions   **/
            /*****************************/

            /** Adds the currently selected messages to the tag selected via the Message Tag dialog */
            $scope.addToTagDialog = function () {
                MessageService.messageTagsDialog(false).result
                    .then(function (tag) {
                        if (tag && !$scope.selection.isEmpty()) {
                            MessageService.addMessagesToTag(tag, $scope.selection.keys)
                                .success(function () {
                                    growl.info("Added messages to " + tag.name, { ttl: 3000 })
                                })
                        }
                    });
            };


            /** Removes the currently selected messages from the tag selected via the Message Tag dialog */
            $scope.removeFromTagDialog = function () {
                MessageService.messageTagsDialog(false).result
                    .then(function (tag) {
                        if (tag && !$scope.selection.isEmpty()) {
                            MessageService.removeMessagesFromTag(tag, $scope.selection.keys)
                                .success(function () {
                                    growl.info("Removed message from " + tag.name, { ttl: 3000 })
                                })
                        }
                    });
            };


            /*****************************/
            /** Print PDF functions     **/
            /*****************************/


            /** Opens the message print dialog */
            $scope.pdf = function () {
                MessageService.messagePrintDialog($scope.totalMessageNo, true).result
                    .then($scope.generatePdf);
            };


            /** Opens the message print dialog for the current selection */
            $scope.pdfForSelection = function () {
                MessageService.messagePrintDialog($scope.selection.size(), true).result
                    .then(function (printParams) {
                        // Generate a temporary, short-lived message tag for the selection
                        MessageService.createTempMessageTag($scope.selection.keys)
                            .success(function (tag) {
                                printParams += '&tag=' + encodeURIComponent(tag.tagId);
                                $scope.generatePdf(printParams);
                            })
                    });
            };


            /** Download the PDF for the current search result */
            $scope.generatePdf = function (printParams) {
                printParams += '&' + $scope.toRequestFilterParameters();
                $window.location = '/rest/message-reports/report.pdf?' + printParams;
            };


            /*****************************/
            /** Send Mail functions     **/
            /*****************************/


            /** Opens the message send-mail dialog */
            $scope.sendMailDialog = function () {
                var params = $scope.toRequestFilterParameters();
                if (params.length > 0) {
                    params += '&';
                }
                params += 'lang=' + $rootScope.language;

                MessageService.messageMailDialog($scope.totalMessageNo, params);
            };


            /*****************************/
            /** Message Comparison      **/
            /*****************************/


            /** Opens a dialog that allows the editor to compare two selected messages **/
            $scope.compareMessages = function () {
                if ($scope.selectionList.length == 2) {
                    MessageService.compareMessagesDialog($scope.selectionList[0].id, $scope.selectionList[1].id);
                }
            };


            /*****************************/
            /** Export/Import functions **/
            /*****************************/


            /** Exports the current selection as a Zip archive **/
            $scope.exportSelection = function () {
                // Generate a temporary, short-lived message tag for the selection
                MessageService.createTempMessageTag($scope.selection.keys)
                    .success(function (tag) {
                        var params = 'tag=' + encodeURIComponent(tag.tagId);
                        $scope.exportMessages(params);
                    })
            };


            /** Exports the current search result as a Zip archive **/
            $scope.exportMessages = function (params) {
                MessageService.authTicket()
                    .success(function (ticket) {
                        if (!params) {
                            params = $scope.toRequestFilterParameters();
                        }
                        if (params.length > 0) {
                            params += '&';
                        }
                        params += 'lang=' + $rootScope.language
                            + '&ticket=' + encodeURIComponent(ticket);

                        $window.location = '/rest/message-io/export.zip?' + params;
                    });
            };


            /** Import a zip archive with messages **/
            $scope.importMessages = function () {
                MessageService.importMessagesDialog();
            };


            /*****************************/
            /** Bulk status update      **/
            /*****************************/


            /** Bulk-updates the status of the selection **/
            $scope.updateStatusDialog = function () {
                MessageService.updateStatusDialog($scope.selection).result
                    .then($scope.filterUpdated);
            };


            /*****************************/
            /** Utility functions       **/
            /*****************************/


            /** Returns the bottom-left point of the current map extent **/
            $scope.extentBottomLeft = function () {
                var extent = $scope.state.map.extent;
                return extent
                    ? { lon: extent[0], lat: extent[1] }
                    : '';
            };


            /** Returns the top-right point of the current map extent **/
            $scope.extentTopRight = function () {
                var extent = $scope.state.map.extent;
                return extent
                    ? { lon: extent[2], lat: extent[3] }
                    : '';
            };


            /** Returns if the user should be allowed to select the given types for filtering */
            $scope.supportsType = function (type) {
                if ($scope.searchDomain) {
                    // When a domain is selected, check if any of the domain message series has the given type
                    var result = false;
                    if ($scope.searchDomain.messageSeries) {
                        for (var x = 0; x < $scope.searchDomain.messageSeries.length; x++) {
                            result |= $scope.searchDomain.messageSeries[x].mainType == type;
                        }
                    }
                    return result;
                }
                // If no domain is selected, any type should be selectable
                return true;
            };


            $scope.supportsNw = function (exclusively) {
                return $scope.supportsType('NW') &&
                    (!exclusively || !$scope.supportsType('NM'));
            };


            $scope.supportsNm = function (exclusively) {
                return $scope.supportsType('NM') &&
                    (!exclusively || !$scope.supportsType('NW'));
            };


            /*****************************/
            /** Message List Handling   **/
            /*****************************/


            /**
             * Scans through the search result and marks all messages that should potentially
             * display an area head line
             **/
            $scope.checkGroupByArea = function (maxLevels) {
                maxLevels = maxLevels || 2;
                var lastAreaId = undefined;
                if ($scope.messageList && $scope.messageList.length > 0 && $scope.state.sortBy == 'AREA') {
                    for (var m = 0; m < $scope.messageList.length; m++) {
                        var msg = $scope.messageList[m];
                        if (msg.areas && msg.areas.length > 0) {
                            var msgArea = msg.areas[0];
                            var areas = [];
                            for (var area = msgArea; area !== undefined; area = area.parent) {
                                areas.unshift(area);
                            }
                            if (areas.length > 0) {
                                area = areas[Math.min(areas.length - 1, maxLevels - 1)];
                                if (!lastAreaId || area.id != lastAreaId) {
                                    lastAreaId = area.id;
                                    msg.areaHeading = area;
                                }
                            }
                        } else {
                            // Use a special "General" heading for messages without an area
                            if (lastAreaId != -999999) {
                                lastAreaId = -999999;
                                msg.areaHeading =  { id: -999999 };
                            }
                        }
                    }
                }
            };


            // Monitor changes to the state
            $scope.$watch("state", $scope.filterUpdated, true);
            // Also monitor base data being loaded when the page first loads
            $scope.$watch($scope.pendingInitDataNo, $scope.filterUpdated, true);


            /** Called when the state have been updated **/
            $scope.refreshMessages = function (append) {

                var params = $scope.toRequestFilterParameters();

                var searchParams = params;
                if ($scope.state.map.enabled) {
                    if (searchParams.length > 0) {
                        searchParams += '&';
                    }
                    searchParams += 'viewMode=map&includeNoPos=true';
                }

                MessageService.search(searchParams, $scope.page, $scope.maxSize)
                    .success(function (result) {
                        if (!append) {
                            $scope.messageList.length = 0;
                        }
                        for (var x = 0; x < result.data.length; x++) {
                            var message = result.data[x];
                            $scope.messageList.push(message);

                            // Replace any selected message with the new version
                            if ($scope.selection.get(message.id) !== undefined) {
                                $scope.selection.put(message.id, angular.copy(message));
                            }
                        }
                        $scope.totalMessageNo = result.total;
                        $scope.checkGroupByArea(2);
                    });


                // Update the request parameters
                $location.search(params);

                // Store the last message search url to facilitate 'back-to-list' link
                $scope.recordLastMessageUrl();
            };


            /** Toggle the sort order of the current sort field **/
            $scope.toggleSortOrder = function(sortBy) {
                if (sortBy == $scope.state.sortBy) {
                    $scope.state.sortOrder = $scope.state.sortOrder == 'ASC' ? 'DESC' : 'ASC';
                } else {
                    $scope.state.sortBy = sortBy;
                    $scope.state.sortOrder = 'ASC';
                }
            };


            /** Returns the sort indicator to display for the given field **/
            $scope.sortIndicator = function(sortBy) {
                if (sortBy == $scope.state.sortBy) {
                    return $scope.state.sortOrder == 'DESC' ? '&#9650;' : '&#9660;';
                }
                return "";
            };


            /** Returns if the messages for the given area can be sorted **/
            $scope.canSortAreaMessages = function (area) {
                return $scope.isEditor && area && area.id != -999999;
            };


            /** Sorts the messages withing an area **/
            $scope.sortAreaMessages = function (area) {
                var status = 'PUBLISHED';
                var s = $scope.state.status;
                if (s.enabled) {
                    if (!s.PUBLISHED && s.DRAFT) {
                        status = 'DRAFT';
                    } else if (!s.PUBLISHED && s.VERIFIED) {
                        status = 'VERIFIED';
                    }
                }

                // Get the user to pick an area with a geometry
                MessageService.sortAreaMessagesDialog(area, status)
                    .result.then(function () {
                        $scope.refreshMessages();
                    });
            };


            // Read the request filter parameters
            $scope.readRequestFilterParameters();


            /** Load more messages */
            $scope.loadMore = function () {
                $scope.page++;
                $scope.refreshMessages(true);
            };


            /** Store the last message search url to facilitate 'back-to-list' link **/
            $scope.recordLastMessageUrl = function () {
                var url = $location.url();
                if (url && url.indexOf('/messages/') != -1) {
                    $rootScope.lastMessageSearchUrl = '/#' + url;
                }
            };


            // Only apply the map extent as a filter if the map view mode used
            $scope.$watch(
                function () { return $location.path(); },
                function (newValue) {
                    var wasMap = $scope.state.map.enabled;
                    $scope.state.map.enabled = newValue && newValue.endsWith('/map');
                    $scope.showFilter = newValue && !newValue.endsWith('/selected');
                    var isMap = $scope.state.map.enabled;
                    $scope.maxSize = isMap ? 1000 : 100;
                    if (wasMap != isMap) {
                        $scope.messageList.length = 0;
                    }

                    // Update the location url with the search parameters
                    $location.search($scope.toRequestFilterParameters());
                    $scope.recordLastMessageUrl();
                },
                true);

        }]);
