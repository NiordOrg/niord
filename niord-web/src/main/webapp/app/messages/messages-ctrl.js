
/**
 * The message list controller
 *
 * Whenever the message list filter and map settings are updated, the corresponding request parameters
 * are updated. This allows e.g. for bookmarking of the current state.
 */
angular.module('niord.messages')
    .controller('MessageListCtrl', ['$scope', '$rootScope', '$location', '$http', '$timeout',
                'AuthService', 'FilterService', 'MessageService', 'AtonService',
        function ($scope, $rootScope, $location, $http, $timeout,
                  AuthService, FilterService, MessageService, AtonService) {
            'use strict';

            var loadTimer;

            $scope.showFilter = true;
            $scope.messageList = [];
            $scope.totalMessageNo = 0;
            $scope.filterNames = [ 'text', 'type', 'status', 'tag', 'aton', 'chart', 'area', 'category', 'date' ];
            $scope.state = {

                /** Map state. Also serves as a mandatory filter in map mode **/
                map : {
                    enabled: true,
                    reloadMap : false // can be used to trigger a map reload
                },

                /** Filter state **/
                text: {
                    enabled: false,
                    focusField: '#query',
                    query: ''
                },
                type: {
                    enabled: false,
                    mainType: '',
                    nwType: '',
                    nmType: ''
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
                        filter.nwType = '';
                        filter.nmType = '';
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
                if (s.text.enabled) {
                    params += '&query=' + encodeURIComponent(s.text.query);
                }
                if (s.type.enabled) {
                    params += '&mainType=' + s.type.mainType;
                    if (s.type.nwType) {
                        params += '&type=' + s.type.nwType;
                    }
                    if (s.type.nmType) {
                        params += '&type=' + s.type.nmType;
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
                if (params.query) {
                    s.text.enabled = true;
                    s.text.query = params.query;
                }
                if (params.type) {
                    s.type.enabled = true;
                    s.type.mainType = params.mainType;
                    if (params.type && params.mainType == 'NW') {
                        s.type.nwType = params.nwType;
                    }
                    if (params.nmType && params.mainType == 'NM') {
                        s.type.nmType = params.type;
                    }
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
                    $http.get('/rest/tags/' + tags).then(function(response) {
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

            };

            // Called when the filter is updated
            $scope.filterUpdated = function () {
                // Enforce validity of the filter selection
                if ($scope.state.type.mainType == '') {
                    $scope.state.type.nwType = '';
                    $scope.state.type.nmType = '';
                }

                if (loadTimer) {
                    $timeout.cancel(loadTimer);
                }
                loadTimer = $timeout($scope.refreshMessages, 300);
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


            // Use for area selection
            $scope.areas = [];
            $scope.refreshAreas = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return $http.get(
                    '/rest/areas/search?name=' + encodeURIComponent(name) +
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
                    '/rest/categories/search?name=' + encodeURIComponent(name) + '&lang=' + $rootScope.language + '&limit=10'
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

            /*****************************/
            /** Message List Handling   **/
            /*****************************/


            // Monitor changes to the state
            $scope.$watch("state", $scope.filterUpdated, true);


            // Called when the state have been updated
            $scope.refreshMessages = function () {

                var params = $scope.toRequestFilterParameters();

                MessageService.search(params)
                    .success(function (result) {
                        $scope.messageList.length = 0;
                        for (var x = 0; x < result.data.length; x++) {
                            $scope.messageList.push(result.data[x]);
                        }
                        $scope.totalMessageNo = result.total;
                    });


                // Update the request parameters
                $location.search(params);
            };


            // Read the request filter parameters
            $scope.readRequestFilterParameters();


            // Only apply the map extent as a filter if the map view mode used
            $scope.$watch(
                function () { return $location.path(); },
                function (newValue) {
                    $scope.state.map.enabled = newValue && newValue.endsWith('/map');
                    $scope.showFilter = newValue && !newValue.endsWith('/selected');
                },
                true);

        }]);
