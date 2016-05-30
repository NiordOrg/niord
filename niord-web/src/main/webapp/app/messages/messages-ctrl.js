
/**
 * The message list controller
 *
 * Whenever the message list filter and map settings are updated, the corresponding request parameters
 * are updated. This allows e.g. for bookmarking of the current state.
 */
angular.module('niord.messages')
    .controller('MessageListCtrl', ['$scope', '$rootScope', '$window', '$location', '$http', '$timeout',
                'AuthService', 'FilterService', 'MessageService', 'AtonService',
        function ($scope, $rootScope, $window, $location, $http, $timeout,
                  AuthService, FilterService, MessageService, AtonService) {
            'use strict';

            var loadTimer;

            $scope.page = 0;
            $scope.maxSize = 100;
            $scope.showFilter = true;
            $scope.messageList = [];
            $scope.totalMessageNo = 0;
            $scope.filterNames = [ 'messageSeries', 'text', 'type', 'status', 'tag', 'aton', 'chart', 'area', 'category', 'date' ];
            if ($rootScope.domain) {
                $scope.filterNames.unshift('domain');
            }
            $scope.state = {

                /** Sorting **/
                sortBy : 'AREA',
                sortOrder : 'ASC',

                /** Map state. Also serves as a mandatory filter in map mode **/
                map : {
                    enabled: true,
                    reloadMap : false // can be used to trigger a map reload
                },
                domain : {
                    enabled: $rootScope.domain !== undefined
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
                    IMPORTED: false,
                    VERIFIED: false,
                    CANCELLED: false,
                    EXPIRED: false,
                    DELETED: false
                },
                tag: {
                    enabled: false,
                    focusField: '#tags > div > input.ui-select-search',
                    tags: []
                },
                aton: {
                    enabled: false,
                    focusField: '#atons > div > input.ui-select-search',
                    atons: []
                },
                chart: {
                    enabled: false,
                    focusField: '#charts > div > input.ui-select-search',
                    charts: []
                },
                messageSeries: {
                    enabled: false,
                    focusField: '#messageSeries > div > input.ui-select-search',
                    series: []
                },
                area: {
                    enabled: false,
                    focusField: '#areas > div > input.ui-select-search',
                    areas: []
                },
                category: {
                    enabled: false,
                    focusField: '#categories > div > input.ui-select-search',
                    categories: []
                },
                date: {
                    enabled: false,
                    fromDate: undefined,
                    toDate: undefined
                }
            };


            /** Destroy any pending message loading operations **/
            $scope.$on('$destroy', function() {
                if (angular.isDefined(loadTimer)) {
                    $timeout.cancel(loadTimer);
                    loadTimer = undefined;
                }
            });

            /*****************************/
            /** Filter Handling         **/
            /*****************************/

            // Enables or disables the given filter
            $scope.enableFilter = function (name, enabled) {
                var filter = $scope.state[name];
                filter.enabled = enabled;
                if (!enabled) {
                    $scope.clearFilter(name);
                } else if (filter.focusField) {
                    $timeout(function () { $(filter.focusField).focus() });
                }
            };


            // Clears the given filter
            $scope.clearFilter = function (name) {
                var filter = $scope.state[name];
                filter.enabled = false;
                switch (name) {
                    case 'text':
                        filter.query = '';
                        break;
                    case 'type':
                        filter.mainType = '';
                        filter.PERMANENT_NOTICE = false;
                        filter.TEMPORARY_NOTICE = false;
                        filter.PRELIMINARY_NOTICE = false;
                        filter.MISCELLANEOUS_NOTICE = false;
                        filter.COASTAL_WARNING = false;
                        filter.SUBAREA_WARNING = false;
                        filter.NAVAREA_WARNING = false;
                        filter.LOCAL_WARNING = false;
                        break;
                    case 'status':
                        filter.PUBLISHED = false;
                        filter.DRAFT = false;
                        filter.IMPORTED = false;
                        filter.VERIFIED = false;
                        filter.CANCELLED = false;
                        filter.EXPIRED = false;
                        filter.DELETED = false;
                        break;
                    case 'tag':
                        filter.tags = [];
                        break;
                    case 'aton':
                        filter.atons = [];
                        break;
                    case 'chart':
                        filter.charts = [];
                        break;
                    case 'messageSeries':
                        filter.sereis = [];
                        break;
                    case 'area':
                        filter.areas = [];
                        break;
                    case 'category':
                        filter.categories = [];
                        break;
                    case 'date':
                        filter.fromDate = undefined;
                        filter.toDate = undefined;
                        break;
                }
            };

            // Clears all filters
            $scope.clearAllFilters = function () {
                angular.forEach($scope.filterNames, function (name) {
                    $scope.clearFilter(name);
                })
            };

            // Converts the filters into a request parameter string
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
                if ($rootScope.domain && !s.domain.enabled) {
                    params += '&domain=false'
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
                    if (s.status.IMPORTED) {
                        params += '&status=IMPORTED';
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
                    angular.forEach(s.messageSeries.series, function (s) {
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
                }

                // Skip first '&'
                return params.length > 0 ? params.substr(1) : '';
            };


            // Parses the request parameters into a filter
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
                if ($rootScope.domain) {
                    s.domain.enabled = params.domain != 'false';
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
                    var tags = (typeof params.tag === 'string') ? params.tag : params.tag.join();
                    $http.get('/rest/tags/tag/' + tags).then(function(response) {
                        s.tag.tags = response.data;
                    });
                }
                if (params.aton && params.aton.length > 0) {
                    s.aton.enabled = true;
                    var atons = (typeof params.aton === 'string') ? params.aton : params.aton.join();
                    $http.get('/rest/atons/' + atons + '?lang=' + $rootScope.language)
                        .then(function(response) {
                            s.aton.atons = response.data;
                        });
                }
                if (params.chart && params.chart.length > 0) {
                    s.chart.enabled = true;
                    var charts = (typeof params.chart === 'string') ? params.chart : params.chart.join();
                    $http.get('/rest/charts/search/' + charts + '?lang=' + $rootScope.language + '&limit=10')
                        .then(function(response) {
                            s.chart.charts = response.data;
                        });
                }
                if (params.messageSeries && params.messageSeries.length > 0) {
                    s.messageSeries.enabled = true;
                    var series = (typeof params.messageSeries === 'string') ? params.messageSeries : params.messageSeries.join();
                    $http.get('/rest/message-series/search/' + series + '?lang=' + $rootScope.language + '&limit=10')
                        .then(function(response) {
                            s.messageSeries.series = response.data;
                        });
                }
                if (params.area && params.area.length > 0) {
                    s.area.enabled = true;
                    var areas = (typeof params.area === 'string') ? params.area : params.area.join();
                    $http.get('/rest/areas/search/' + areas + '?lang=' + $rootScope.language + '&limit=10')
                        .then(function(response) {
                            s.area.areas = response.data;
                        });
                }
                if (params.category && params.category.length > 0) {
                    s.category.enabled = true;
                    var categories = (typeof params.category === 'string') ? params.category : params.category.join();
                    $http.get('/rest/categories/search/' + categories + '?lang=' + $rootScope.language + '&limit=10')
                        .then(function(response) {
                            s.category.categories = response.data;
                        });
                }
                if (params.fromDate || params.toDate) {
                    s.date.enabled = true;
                    s.date.fromDate = params.fromDate ? parseInt(params.fromDate) : undefined;
                    s.date.toDate = params.toDate ? parseInt(params.toDate) : undefined;
                }
                if (params.sortBy) {
                    s.sortBy = params.sortBy;
                }
                if (params.sortOrder) {
                    s.sortOrder = params.sortOrder;
                }
            };

            
            // Called when the filter is updated
            $scope.filterUpdated = function () {
                
                // Enforce validity of the filter selection
                if ($scope.state.type.mainType == 'NW') {
                    $scope.state.type.PERMANENT_NOTICE = false;
                    $scope.state.type.TEMPORARY_NOTICE = false;
                    $scope.state.type.PRELIMINARY_NOTICE = false;
                    $scope.state.type.MISCELLANEOUS_NOTICE = false;
                }
                if ($scope.state.type.mainType == 'NM') {
                    $scope.state.type.COASTAL_WARNING = false;
                    $scope.state.type.SUBAREA_WARNING = false;
                    $scope.state.type.NAVAREA_WARNING = false;
                    $scope.state.type.LOCAL_WARNING = false;
                }
                
                if (!$scope.state.domain.enabled) {
                    $scope.state.status.PUBLISHED = false;
                    $scope.state.status.DRAFT = false;
                    $scope.state.status.IMPORTED = false;
                    $scope.state.status.VERIFIED = false;
                    $scope.state.status.CANCELLED = false;
                    $scope.state.status.EXPIRED = false;
                    $scope.state.status.DELETED = false;
                }

                if (loadTimer) {
                    $timeout.cancel(loadTimer);
                }
                $scope.page = 0;
                loadTimer = $timeout($scope.refreshMessages, 100);
            };

            
            // Use for tag selection
            $scope.tags = [];
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


            // Use for charts selection
            $scope.charts = [];
            $scope.refreshCharts = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/charts/search?name=' + encodeURIComponent(name) + '&lang=' + $rootScope.language + '&limit=10'
                ).then(function(response) {
                    $scope.charts = response.data;
                });
            };


            // Use for message series selection
            $scope.messageSeries = [];
            $scope.refreshMessageSeries = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/message-series/search?name=' + encodeURIComponent(name) +
                    '&domain=' + $scope.state.domain.enabled +
                    '&lang=' + $rootScope.language +
                    '&limit=10'
                ).then(function(response) {
                    $scope.messageSeries = response.data;
                });
            };


            // Use for area selection
            $scope.areas = [];
            $scope.refreshAreas = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/areas/search?name=' + encodeURIComponent(name) +
                    '&domain=' + $scope.state.domain.enabled +
                    '&lang=' + $rootScope.language +
                    '&limit=10'
                ).then(function(response) {
                    $scope.areas = response.data;
                });
            };

            // Use for category selection
            $scope.categories = [];
            $scope.refreshCategories = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/categories/search?name=' + encodeURIComponent(name) +
                    '&domain=' + $scope.state.domain.enabled +
                    '&lang=' + $rootScope.language +
                    '&limit=10'
                ).then(function(response) {
                    $scope.categories = response.data;
                });
            };

            // Recursively formats the names of the parent lineage for areas and categories
            $scope.formatParents = function(child) {
                var txt = undefined;
                if (child) {
                    txt = (child.descs && child.descs.length > 0) ? child.descs[0].name : 'N/A';
                    if (child.parent) {
                        txt = $scope.formatParents(child.parent) + " - " + txt;
                    }
                }
                return txt;
            };


            /*****************************/
            /** Named Filters Handling  **/
            /*****************************/

            $scope.loadNamedFilters = function () {
                if (AuthService.loggedIn) {
                    FilterService
                        .getFilters()
                        .success(function (filters) { $scope.namedFilters = filters; });
                }
            };

            $scope.namedFilters = [];
            $scope.loadNamedFilters();

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

            $scope.removeNamedFilter = function (filter) {
                FilterService
                    .removeFilter(filter.id)
                    .success($scope.loadNamedFilters);
            };

            $scope.applyNamedFilter = function (filter) {
                $scope.clearAllFilters();
                $location.search(filter.parameters);
                $scope.readRequestFilterParameters();
                $scope.state.map.reloadMap = true;
            };


            /*****************************/
            /** Utility functions       **/
            /*****************************/


            /** Opens the message print dialog */
            $scope.pdf = function () {
                MessageService.messagePrintDialog($scope.totalMessageNo).result
                    .then($scope.generatePdf);
            };


            /** Download the PDF for the current search result */
            $scope.generatePdf = function (printSettings) {

                MessageService.pdfTicket()
                    .success(function (ticket) {
                        var params = $scope.toRequestFilterParameters();
                        if (params.length > 0) {
                            params += '&';
                        }
                        params += 'sortBy=' + $scope.state.sortBy
                            + '&sortOrder=' + $scope.state.sortOrder
                            + '&lang=' + $rootScope.language
                            + '&ticket=' + encodeURIComponent(ticket);

                        if (printSettings && printSettings.pageOrientation) {
                            params += '&pageOrientation=' + printSettings.pageOrientation;
                        }
                        if (printSettings && printSettings.pageSize) {
                            params += '&pageSize=' + printSettings.pageSize;
                        }

                        $window.location = '/rest/messages/search.pdf?' + params;
                    });
            };


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


            /** Opens the tags dialog */
            $scope.openTagsDialog = function () {
                MessageService.messageTagsDialog().result
                    .then(function (tag) {
                        if (tag) {
                            $location.search('tag=' + tag.tagId);
                            $scope.readRequestFilterParameters();
                        }
                    });
            };


            /** Returns if the user should be allowed to select the given types for filtering */
            $scope.supportsType = function (type) {
                if ($rootScope.domain && $scope.state.domain.enabled) {
                    // When a domain is selected, check if any of the domain message series has the given type
                    var result = false;
                    if ($rootScope.domain.messageSeries) {
                        for (var x = 0; x < $rootScope.domain.messageSeries.length; x++) {
                            result |= $rootScope.domain.messageSeries[x].mainType == type;
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


            // Scans through the search result and marks all messages that should potentially display an area head line
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
                        }
                    }
                }
            };


            // Monitor changes to the state
            $scope.$watch("state", $scope.filterUpdated, true);


            // Called when the state have been updated
            $scope.refreshMessages = function (append) {

                var params = $scope.toRequestFilterParameters();
                if (params.length > 0) {
                    params += '&';
                }
                params += 'sortBy=' + $scope.state.sortBy + '&sortOrder=' + $scope.state.sortOrder;

                var searchParams = params;
                if ($scope.state.map.enabled) {
                    searchParams += '&viewMode=map&includeGeneral=true';
                }

                MessageService.search(searchParams, $scope.page, $scope.maxSize)
                    .success(function (result) {
                        if (!append) {
                            $scope.messageList.length = 0;
                        }
                        for (var x = 0; x < result.data.length; x++) {
                            $scope.messageList.push(result.data[x]);
                        }
                        $scope.totalMessageNo = result.total;
                        $scope.checkGroupByArea(2);
                    });


                // Update the request parameters
                $location.search(params);
            };


            // Toggle the sort order of the current sort field
            $scope.toggleSortOrder = function(sortBy) {
                if (sortBy == $scope.state.sortBy) {
                    $scope.state.sortOrder = $scope.state.sortOrder == 'ASC' ? 'DESC' : 'ASC';
                } else {
                    $scope.state.sortBy = sortBy;
                    $scope.state.sortOrder = 'ASC';
                }
            };


            // Returns the sort indicator to display for the given field
            $scope.sortIndicator = function(sortBy) {
                if (sortBy == $scope.state.sortBy) {
                    return $scope.state.sortOrder == 'DESC' ? '&#9650;' : '&#9660';
                }
                return "";
            };


            // Read the request filter parameters
            $scope.readRequestFilterParameters();


            /** Load more messages */
            $scope.loadMore = function () {
                $scope.page++;
                $scope.refreshMessages(true);
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
                },
                true);

        }])


    /*******************************************************************
     * Controller that handles displaying message details in a dialog
     *******************************************************************/
    .controller('MessageDialogCtrl', ['$scope', '$window', 'growl', 'MessageService', 'messageId', 'messages',
        function ($scope, $window, growl, MessageService, messageId, messages) {
            'use strict';

            $scope.warning = undefined;
            $scope.messages = messages;
            $scope.pushedMessageIds = [];
            $scope.pushedMessageIds[0] = messageId;

            $scope.msg = undefined;
            $scope.index = $.inArray(messageId, messages);
            $scope.showNavigation = $scope.index >= 0;

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
                    })
                    .error(function (data) {
                        $scope.msg = undefined;
                        growl.error('Message Lookup Failed', { ttl: 5000 });
                    });
            };

            $scope.loadMessageDetails();

        }])


    /*******************************************************************
     * Controller that handles displaying message tags in a dialog
     *******************************************************************/
    .controller('MessageTagsDialogCtrl', ['$scope', 'growl', 'MessageService', 'AuthService',
        function ($scope, growl, MessageService, AuthService) {
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


            // Adds a new tag
            $scope.addTag = function () {
                $scope.data.editMode = 'add';
                $scope.data.editTag = { tagId: '', type: 'PRIVATE', expiryDate: undefined };
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
                $scope.$close(tag);
            };

        }])



    /*******************************************************************
     * Controller that handles the message Print dialog
     *******************************************************************/
    .controller('MessagePrintDialogCtrl', ['$scope', '$document', '$window', 'total',
        function ($scope, $document, $window, total) {
            'use strict';

            $scope.totalMessageNo = total;

            $scope.data = {
                pageSize : 'a4',
                pageOrientation: 'portrait'
            };

            if ($window.localStorage.printSettings) {
                angular.copy(angular.fromJson($window.localStorage.printSettings), $scope.data);
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

        }]);


