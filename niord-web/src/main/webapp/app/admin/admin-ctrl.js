/**
 * The admin controllers.
 */
angular.module('niord.admin')

    /**
     * ********************************************************************************
     * CommonAdminCtrl
     * ********************************************************************************
     * Common Admin Controller
     * Will periodically reload batch status to display the number of running batch jobs in the admin page menu
     */
    .controller('CommonAdminCtrl', ['$scope', '$interval', 'AdminBatchService',
        function ($scope, $interval, AdminBatchService) {
            'use strict';

            $scope.batchStatus = {
                runningExecutions: 0,
                types: []
            };

            // Loads the batch status
            $scope.loadBatchStatus = function () {
                AdminBatchService.getBatchStatus()
                    .success(function (status) {
                        $scope.batchStatus = status;
                    });
            };

            // Reload status every 10 seconds
            if ($scope.hasRole('admin')) {
                // Refresh batch status every 10 seconds
                var interval = $interval($scope.loadBatchStatus, 10 * 1000);

                // Terminate the timer
                $scope.$on("$destroy", function() {
                    if (interval) {
                        $interval.cancel(interval);
                    }
                });

                // Initial load
                $scope.loadBatchStatus();
            }

        }])



    /**
     * ********************************************************************************
     * ChartsAdminCtrl
     * ********************************************************************************
     * Charts Admin Controller
     * Controller for the Admin Charts page
     */
    .controller('ChartsAdminCtrl', ['$scope', 'growl', 'AdminChartService', 'DialogService', 'UploadFileService',
        function ($scope, growl, AdminChartService, DialogService, UploadFileService) {
            'use strict';

            $scope.allCharts = [];
            $scope.chart = undefined; // The chart being edited
            $scope.chartFeatureCollection = { type: 'FeatureCollection', features: [] };
            $scope.editMode = 'add';

            // Pagination
            $scope.charts = [];
            $scope.pageSize = 10;
            $scope.currentPage = 1;
            $scope.chartNo = 0;
            $scope.search = '';


            /** Loads the charts from the back-end */
            $scope.loadCharts = function() {
                $scope.chart = undefined;
                AdminChartService
                    .getCharts()
                    .success(function (charts) {
                        $scope.allCharts = charts;
                        $scope.pageChanged();
                    });
            };


            /** Returns if the string matches the given chart property */
            function match(chartProperty, str) {
                var txt = (chartProperty) ? "" + chartProperty : "";
                return txt.toLowerCase().indexOf(str.toLowerCase()) >= 0;
            }


            /** Called whenever chart pagination changes */
            $scope.pageChanged = function() {
                var search = $scope.search.toLowerCase();
                var filteredCharts = $scope.allCharts.filter(function (chart) {
                    return match(chart.chartNumber, search) ||
                        match(chart.internationalNumber, search) ||
                        match(chart.horizontalDatum, search) ||
                        match(chart.name, search);
                });
                $scope.chartNo = filteredCharts.length;
                $scope.charts = filteredCharts.slice(
                    $scope.pageSize * ($scope.currentPage - 1),
                    Math.min($scope.chartNo, $scope.pageSize * $scope.currentPage));
            };
            $scope.$watch("search", $scope.pageChanged, true);


            /** Adds a new chart **/
            $scope.addChart = function () {
                $scope.editMode = 'add';
                $scope.chart = {
                    chartNumber: undefined,
                    internationalNumber: undefined,
                    horizontalDatum: 'WGS84'
                };
                $scope.chartFeatureCollection.features.length = 0;
            };


            /** Edits a chart **/
            $scope.editChart = function (chart) {
                $scope.editMode = 'edit';
                $scope.chart = angular.copy(chart);
                $scope.chartFeatureCollection.features.length = 0;
                if ($scope.chart.geometry) {
                    var feature = {type: 'Feature', geometry: $scope.chart.geometry, properties: {}};
                    $scope.chartFeatureCollection.features.push(feature);
                }
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving chart", { ttl: 5000 });
            };


            /** Saves the current chart being edited */
            $scope.saveChart = function () {

                // Update the chart geometry
                delete $scope.chart.geometry;
                if ($scope.chartFeatureCollection.features.length > 0 &&
                    $scope.chartFeatureCollection.features[0].geometry) {
                    $scope.chart.geometry = $scope.chartFeatureCollection.features[0].geometry;
                }

                if ($scope.chart && $scope.editMode == 'add') {
                    AdminChartService
                        .createChart($scope.chart)
                        .success($scope.loadCharts)
                        .error($scope.displayError);
                } else if ($scope.chart && $scope.editMode == 'edit') {
                    AdminChartService
                        .updateChart($scope.chart)
                        .success($scope.loadCharts)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given chart */
            $scope.deleteChart = function (chart) {
                DialogService.showConfirmDialog(
                    "Delete Chart?", "Delete chart number '" + chart.chartNumber + "'?")
                    .then(function() {
                        AdminChartService
                            .deleteChart(chart)
                            .success($scope.loadCharts)
                            .error($scope.displayError);
                    });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadChartsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Charts File',
                    '/rest/charts/upload-charts',
                    'json');
            };
        }])



    /**
     * ********************************************************************************
     * AreaAdminCtrl
     * ********************************************************************************
     * Area Admin Controller
     * Controller for the Admin Areas page
     */
    .controller('AreaAdminCtrl', ['$scope', 'growl', 'LangService', 'AdminAreaService', 'DialogService', 'UploadFileService',
        function ($scope, growl, LangService, AdminAreaService, DialogService, UploadFileService) {
            'use strict';

            $scope.areas = [];
            $scope.area = undefined;
            $scope.editArea = undefined;
            $scope.action = "edit";
            $scope.areaFilter = '';
            $scope.areaFeatureCollection = { type: 'FeatureCollection', features: [] };


            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
            }


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Area operation failed", { ttl: 5000 });
            };


            /** If the area form is visible set it to be pristine */
            $scope.setPristine = function () {
                if ($scope.areaForm) {
                    $scope.areaForm.$setPristine();
                }
            };

            /** Load the areas */
            $scope.loadAreas = function() {
                AdminAreaService
                    .getAreas()
                    .success(function (areas) {
                        $scope.areas = areas;
                        $scope.area = undefined;
                        $scope.editArea = undefined;
                        $scope.setPristine();
                    })
                    .error ($scope.displayError);
            };


            /** Creates a new area */
            $scope.newArea = function() {
                $scope.action = "add";
                $scope.editArea = LangService.checkDescs({}, ensureNameField);
                if ($scope.area) {
                    $scope.editArea.parent = { id: $scope.area.id };
                }
                $scope.areaFeatureCollection.features.length = 0;
                $scope.setPristine();
            };


            /** Called when an areas is selected */
            $scope.selectArea = function (area) {
                AdminAreaService
                    .getArea(area)
                    .success(function (data) {
                        $scope.action = "edit";
                        $scope.area = LangService.checkDescs(data, ensureNameField);
                        $scope.editArea = angular.copy($scope.area);
                        $scope.areaFeatureCollection.features.length = 0;
                        if ($scope.editArea.geometry) {
                            var feature = {type: 'Feature', geometry: $scope.editArea.geometry, properties: {}};
                            $scope.areaFeatureCollection.features.push(feature);
                        }
                        $scope.setPristine();
                        $scope.$$phase || $scope.$apply();
                    })
                    .error($scope.displayError);
            };


            /** Called when an area has been dragged to a new parent area */
            $scope.moveArea = function (area, parent) {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Move Area?", "Move " + area.descs[0].name + " to " + ((parent) ? parent.descs[0].name : "the root") + "?")
                    .then(function() {
                        AdminAreaService
                            .moveArea(area.id, (parent) ? parent.id : undefined)
                            .success($scope.loadAreas)
                            .error($scope.displayError);
                    });
            };


            /** Called when the sibling area sort order has changed for the currently selected area */
            $scope.changeSiblingSortOrder = function (moveUp) {
                AdminAreaService
                    .changeSortOrder($scope.area.id, moveUp)
                    .success($scope.loadAreas)
                    .error($scope.displayError);
            };


            /** Will query the back-end to recompute the tree sort order */
            $scope.recomputeTreeSortOrder = function () {
                AdminAreaService
                    .recomputeTreeSortOrder()
                    .success(function () {
                        growl.info('Tree Sort Order Updated', { ttl: 3000 });
                    })
                    .error($scope.displayError);
            };


            /** Saves the current area */
            $scope.saveArea = function () {
                // Update the area geometry
                delete $scope.editArea.geometry;
                if ($scope.areaFeatureCollection.features.length > 0 &&
                    $scope.areaFeatureCollection.features[0].geometry) {
                    $scope.editArea.geometry = $scope.areaFeatureCollection.features[0].geometry;
                }
                // Handle blank type
                if ($scope.editArea.type == '') {
                    delete $scope.editArea.type;
                }

                if ($scope.action == 'add') {
                    AdminAreaService
                        .createArea($scope.editArea)
                        .success($scope.loadAreas)
                        .error ($scope.displayError);

                } else {
                    AdminAreaService
                        .updateArea($scope.editArea)
                        .success($scope.loadAreas)
                        .error ($scope.displayError);
                }
            };


            /** Called when the area geometry editor is saved */
            $scope.geometrySaved = function () {
                if ($scope.areaForm) {
                    $scope.areaForm.$setDirty();
                }
            };


            /** Deletes the current area */
            $scope.deleteArea = function () {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Delete Area?", "Delete area " + $scope.area.descs[0].name + "?")
                    .then(function() {
                        AdminAreaService
                            .deleteArea($scope.editArea)
                            .success($scope.loadAreas)
                            .error ($scope.displayError);
                    });
            };


            /** Opens the upload-areas dialog **/
            $scope.uploadAreasDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Area JSON File',
                    '/rest/areas/upload-areas',
                    'json');
            };
        }])


    /**
     * ********************************************************************************
     * CategoryAdminCtrl
     * ********************************************************************************
     * Category Admin Controller
     * Controller for the Admin Categories page
     */
    .controller('CategoryAdminCtrl', ['$scope', 'growl', 'LangService', 'AdminCategoryService', 'DialogService', 'UploadFileService',
        function ($scope, growl, LangService, AdminCategoryService, DialogService, UploadFileService) {
            'use strict';

            $scope.categories = [];
            $scope.category = undefined;
            $scope.editCategory = undefined;
            $scope.action = "edit";
            $scope.categoryFilter = '';


            // Used to ensure that description entities have a "name" field
            function ensureNameField(desc) {
                desc.name = '';
            }


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Category operation failed", { ttl: 5000 });
            };


            /** If the category form is visible set it to be pristine */
            $scope.setPristine = function () {
                if ($scope.categoryForm) {
                    $scope.categoryForm.$setPristine();
                }
            };

            /** Load the categories */
            $scope.loadCategories = function() {
                AdminCategoryService
                    .getCategories()
                    .success(function (categories) {
                        $scope.categories = categories;
                        $scope.category = undefined;
                        $scope.editCategory = undefined;
                        $scope.setPristine();
                    })
                    .error ($scope.displayError);
            };


            /** Creates a new category */
            $scope.newCategory = function() {
                $scope.action = "add";
                $scope.editCategory = LangService.checkDescs({}, ensureNameField);
                if ($scope.category) {
                    $scope.editCategory.parent = { id: $scope.category.id };
                }
                $scope.setPristine();
            };


            /** Called when an categories is selected */
            $scope.selectCategory = function (category) {
                AdminCategoryService
                    .getCategory(category)
                    .success(function (data) {
                        $scope.action = "edit";
                        $scope.category = LangService.checkDescs(data, ensureNameField);
                        $scope.editCategory = angular.copy($scope.category);
                        $scope.setPristine();
                        $scope.$$phase || $scope.$apply();
                    })
                    .error($scope.displayError);
            };


            /** Called when an category has been dragged to a new parent category */
            $scope.moveCategory = function (category, parent) {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Move Category?", "Move " + category.descs[0].name + " to " + ((parent) ? parent.descs[0].name : "the root") + "?")
                    .then(function() {
                        AdminCategoryService
                            .moveCategory(category.id, (parent) ? parent.id : undefined)
                            .success($scope.loadCategories)
                            .error($scope.displayError);
                    });
            };


            /** Saves the current category */
            $scope.saveCategory = function () {
                if ($scope.action == 'add') {
                    AdminCategoryService
                        .createCategory($scope.editCategory)
                        .success($scope.loadCategories)
                        .error ($scope.displayError);

                } else {
                    AdminCategoryService
                        .updateCategory($scope.editCategory)
                        .success($scope.loadCategories)
                        .error ($scope.displayError);
                }
            };


            /** Deletes the current category */
            $scope.deleteCategory = function () {

                // Get confirmation
                DialogService.showConfirmDialog(
                    "Delete Category?", "Delete category " + $scope.category.descs[0].name + "?")
                    .then(function() {
                        AdminCategoryService
                            .deleteCategory($scope.editCategory)
                            .success($scope.loadCategories)
                            .error ($scope.displayError);
                    });
            };


            /** Opens the upload-categories dialog **/
            $scope.uploadCategoriesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Category JSON File',
                    '/rest/categories/upload-categories',
                    'json');
            };
        }])


    /**
     * ********************************************************************************
     * MessageSeriesAdminCtrl
     * ********************************************************************************
     * Message Series Admin Controller
     * Controller for the Admin message series page
     */
    .controller('MessageSeriesAdminCtrl', ['$scope', '$rootScope', 'growl', 'AdminMessageSeriesService', 'DialogService',
        function ($scope, $rootScope, growl, AdminMessageSeriesService, DialogService) {
            'use strict';

            $scope.messageSeries = [];
            $scope.series = undefined;  // The message series being edited
            $scope.editMode = 'add';
            $scope.search = '';


            /** Loads the message series from the back-end */
            $scope.loadMessageSeries = function() {
                $scope.series = undefined;
                AdminMessageSeriesService
                    .getMessageSeries()
                    .success(function (messageSeries) {
                        $scope.messageSeries = messageSeries;
                    });
            };


            /** Update the MRN prefix and suffix of the message series being edited */
            $scope.updateMrnFormat = function (updateSuffix) {
                $scope.series.mrnPrefix = $scope.series.mainType == 'NW'
                    ? $rootScope.nwMrnPrefix
                    : $rootScope.nmMrnPrefix;
                if (updateSuffix) {
                    $scope.series.mrnSuffix = '';
                    if ($scope.series.mrnFormat &&
                        $scope.series.mrnFormat.substring(0, $scope.series.mrnPrefix.length) === $scope.series.mrnPrefix) {
                        $scope.series.mrnSuffix = $scope.series.mrnFormat.substring($scope.series.mrnPrefix.length);
                    }
                }
            };


            /** Called whenever the MRN suffix gets updated */
            $scope.mrnSuffixUpdated = function () {
                $scope.series.mrnFormat = $scope.series.mrnPrefix + $scope.series.mrnSuffix;
            };


            /** Inserts the token in the given field */
            $scope.insertToken = function (field, token) {
                if (field === 'mrnSuffix') {
                    $scope.series.mrnSuffix += token;
                } else {
                    $scope.series.shortFormat += token;
                }
                $('#' + field).focus();
                $scope.mrnSuffixUpdated();
                $scope.seriesForm.$setDirty();
            };


            /** Adds a new message series **/
            $scope.addMessageSeries = function () {
                $scope.editMode = 'add';
                $scope.series = {
                    seriesId: '',
                    mainType: 'NW',
                    mrnFormat: '',
                    shortFormat: ''
                };
                $scope.updateMrnFormat(true);
                $scope.seriesForm.$setPristine();
            };


            /** Copies a message series **/
            $scope.copyMessageSeries = function (series) {
                $scope.editMode = 'add';
                $scope.series = angular.copy(series);
                $scope.series.seriesId = undefined;
                $scope.updateMrnFormat(true);
                $scope.seriesForm.$setPristine();
            };


            /** Edits a message series **/
            $scope.editMessageSeries = function (series) {
                $scope.editMode = 'edit';
                $scope.series = angular.copy(series);
                $scope.updateMrnFormat(true);
                $scope.seriesForm.$setPristine();
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving message series", { ttl: 5000 });
            };


            /** Saves the current message series being edited */
            $scope.saveMessageSeries = function () {
                $scope.series.mrnFormat = $scope.series.mrnPrefix + $scope.series.mrnSuffix;
                delete $scope.series.mrnPrefix;
                delete $scope.series.mrnSuffix;

                if ($scope.series && $scope.editMode == 'add') {
                    AdminMessageSeriesService
                        .createMessageSeries($scope.series)
                        .success($scope.loadMessageSeries)
                        .error($scope.displayError);
                } else if ($scope.series && $scope.editMode == 'edit') {
                    AdminMessageSeriesService
                        .updateMessageSeries($scope.series)
                        .success($scope.loadMessageSeries)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given message series */
            $scope.deleteMessageSeries = function (series) {
                DialogService.showConfirmDialog(
                    "Delete message series?", "Delete message series ID '" + series.seriesId + "'?")
                    .then(function() {
                        AdminMessageSeriesService
                            .deleteMessageSeries(series)
                            .success($scope.loadMessageSeries)
                            .error($scope.displayError);
                    });
            };
        }])


    /**
     * ********************************************************************************
     * DomainAdminCtrl
     * ********************************************************************************
     * Domains Admin Controller
     * Controller for the Admin domains page
     */
    .controller('DomainAdminCtrl', ['$scope', 'growl', 'LangService', 'AuthService', 'AdminDomainService', 'DialogService', 'UploadFileService',
        function ($scope, growl, LangService, AuthService, AdminDomainService, DialogService, UploadFileService) {
            'use strict';

            $scope.allDomains = [];
            $scope.domains = [];
            $scope.domain = undefined; // The domain being edited
            $scope.editMode = 'add';
            $scope.search = '';
            $scope.timeZones = moment.tz.names();


            /** Computes the Keycloak URL */
            $scope.getKeycloakUrl = function() {
                // Template http://localhost:8080/auth/admin/master/console/#/realms/niord/clients
                var url = AuthService.keycloak.authServerUrl;
                if (url.charAt(url.length - 1) != '/') {
                    url += '/';
                }
                return url + 'admin/master/console/#/realms/niord/clients';
            };
            $scope.keycloakUrl = $scope.getKeycloakUrl();


            /** Loads the domains from the back-end */
            $scope.loadDomains = function() {
                $scope.domain = undefined;
                AdminDomainService
                    .getDomains()
                    .success(function (domains) {
                        $scope.allDomains = domains;
                        $scope.searchUpdated();
                    });
            };


            /** Returns if the string matches the given domain property */
            function match(domainProperty, str) {
                var txt = (domainProperty) ? "" + domainProperty : "";
                return txt.toLowerCase().indexOf(str.toLowerCase()) >= 0;
            }


            /** Called whenever search criteria changes */
            $scope.searchUpdated = function() {
                var search = $scope.search.toLowerCase();
                $scope.domains = $scope.allDomains.filter(function (domain) {
                    return match(domain.clientId, search) ||
                        match(domain.name, search);
                });
            };
            $scope.$watch("search", $scope.searchUpdated, true);


            /** Adds a new domain **/
            $scope.addDomain = function () {
                $scope.editMode = 'add';
                $scope.domain = {
                    clientId: undefined,
                    name: undefined,
                    timeZone: moment.tz.guess()
                };
                $scope.areas.length = 0;
                $scope.categories.length = 0;
                $scope.messageSeries.length = 0;
            };


            /** Copies a domain **/
            $scope.copyDomain = function (domain) {
                $scope.editMode = 'add';
                $scope.domain = angular.copy(domain);
                $scope.domain.clientId = undefined;
                $scope.areas.length = 0;
                $scope.categories.length = 0;
                $scope.messageSeries.length = 0;
            };


            /** Edits a domain **/
            $scope.editDomain = function (domain) {
                $scope.editMode = 'edit';
                $scope.domain = angular.copy(domain);
                $scope.areas.length = 0;
                $scope.categories.length = 0;
                $scope.messageSeries.length = 0;
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving domain", { ttl: 5000 });
            };


            /** Saves the current domain being edited */
            $scope.saveDomain = function () {

                if ($scope.domain && $scope.editMode == 'add') {
                    AdminDomainService
                        .createDomain($scope.domain)
                        .success($scope.loadDomains)
                        .error($scope.displayError);
                } else if ($scope.domain && $scope.editMode == 'edit') {
                    AdminDomainService
                        .updateDomain($scope.domain)
                        .success($scope.loadDomains)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given domain */
            $scope.deleteDomain = function (domain) {
                DialogService.showConfirmDialog(
                    "Delete domain?", "Delete domain ID '" + domain.clientId + "'?")
                    .then(function() {
                        AdminDomainService
                            .deleteDomain(domain)
                            .success($scope.loadDomains)
                            .error($scope.displayError);
                    });
            };


            /** Creates the domain in Keycloak **/
            $scope.createInKeycloak = function (domain) {
                AdminDomainService
                    .createDomainInKeycloak(domain)
                    .success($scope.loadDomains)
                    .error($scope.displayError);
            };


            /** Opens the upload-domains dialog **/
            $scope.uploadDomainsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Domains File',
                    '/rest/domains/upload-domains',
                    'json');
            };


            /** Use for area selection */
            $scope.areas = [];
            $scope.refreshAreas = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return AdminDomainService
                    .searchAreas(name)
                    .then(function(response) {
                        $scope.areas = response.data;
                    });
            };


            /** Use for category selection */
            $scope.categories = [];
            $scope.refreshCategories = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return AdminDomainService
                    .searchCategories(name)
                    .then(function(response) {
                        $scope.categories = response.data;
                    });
            };


            /** Use for message series selection */
            $scope.messageSeries = [];
            $scope.refreshMessageSeries = function(name) {
                if (!name || name.length == 0) {
                    return [];
                }
                return AdminDomainService
                    .searchMessageSeries(name)
                    .then(function(response) {
                        $scope.messageSeries = response.data;
                    });
            };


            /** Recursively formats the names of the parent lineage for areas and categories */
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
        }])


    /**
     * ********************************************************************************
     * BatchAdminCtrl
     * ********************************************************************************
     * Batch Admin Controller
     */
    .controller('BatchAdminCtrl', ['$scope', '$interval', '$stateParams', '$uibModal', 'AdminBatchService',
        function ($scope, $interval, $stateParams, $uibModal, AdminBatchService) {
            'use strict';

            $scope.batchStatus = {
                runningExecutions: 0,
                types: []
            };
            $scope.batchName = $stateParams.batchName;
            $scope.pageSize = 5;
            $scope.currentPage = 1;
            $scope.executions = [];
            $scope.searchResult = {
                total: 0,
                size: 0,
                data: []
            };


            /**
             * Build a flat list of executions of all batch instances.
             * This will make it easier to present the result in a table
             */
            $scope.buildBatchExecutionList = function () {
                $scope.executions.length = 0;
                angular.forEach($scope.searchResult.data, function (instance) {
                    angular.forEach(instance.executions, function (execution, index) {
                        if (index == 0) {
                            execution.instance = instance;
                        }
                        $scope.executions.push(execution);
                    });
                });
            };

            /** Loads the batch status */
            $scope.loadBatchStatus = function () {
                AdminBatchService.getBatchStatus()
                    .success(function (status) {
                        $scope.batchStatus = status;
                        $scope.loadBatchInstances();
                    });
            };


            /** Refresh batch status every 3 seconds */
            var refreshInterval = $interval($scope.loadBatchStatus, 3 * 1000);


            /** Terminate the timer */
            $scope.$on("$destroy", function() {
                if (refreshInterval) {
                    $interval.cancel(refreshInterval);
                }
            });


            /** Loads "count" more batch instances */
            $scope.loadBatchInstances = function () {
                if ($scope.batchName) {
                    AdminBatchService.getBatchInstances($scope.batchName, $scope.currentPage - 1, $scope.pageSize)
                        .success(function (result) {
                            $scope.searchResult = result;
                            $scope.buildBatchExecutionList();
                        });
                }
            };


            /** Called when the given batch type is displayed */
            $scope.selectBatchType = function (name) {
                $scope.batchName = name;
                $scope.currentPage = 1;
                $scope.searchResult = {
                    total: 0,
                    size: 0,
                    data: []
                };
                $scope.executions.length = 0;
                $scope.loadBatchInstances();
            };

            /** Returns the color to use for a given status **/
            $scope.statusColor = function (execution) {
                var status = execution.batchStatus || 'undef';
                switch (status) {
                    case 'STARTING':
                    case 'STARTED':
                        return 'label-primary';
                    case 'STOPPING':
                    case 'STOPPED':
                        return 'label-warning';
                    case 'FAILED':
                        return 'label-danger';
                    case 'COMPLETED':
                        return 'label-success';
                    case 'ABANDONED':
                        return 'label-default';
                    default:
                        return 'label-default';
                }
            };

            /** Stops the given batch job */
            $scope.stop = function (execution) {
                AdminBatchService
                    .stopBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };


            /** Restarts the given batch job */
            $scope.restart = function (execution) {
                AdminBatchService
                    .restartBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };


            /** Abandon the given batch job */
            $scope.abandon = function (execution) {
                AdminBatchService
                    .abandonBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };

            // Download the instance data file
            $scope.download = function (instance) {
                AdminBatchService
                    .getBatchDownloadTicket()
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.download = instance.fileName;
                        link.href = '/rest/batch/instance/'
                            + instance.instanceId
                            + '/download/'
                            + encodeURI(instance.fileName)
                            + '?ticket=' + ticket;
                        link.click();
                    });
            };

            /** Open the logs dialog */
            $scope.showLogFiles = function (instanceId) {
                $uibModal.open({
                    controller: "BatchLogFileDialogCtrl",
                    templateUrl: "/app/admin/batch-log-file-dialog.html",
                    size: 'l',
                    resolve: {
                        instanceId: function () {
                            return instanceId;
                        }
                    }
                });
            }

        }])


    /**
     * ********************************************************************************
     * BatchLogFileDialogCtrl
     * ********************************************************************************
     * Dialog Controller for the Batch job log file dialog
     */
    .controller('BatchLogFileDialogCtrl', ['$scope', 'AdminBatchService', 'instanceId',
        function ($scope, AdminBatchService, instanceId) {
            'use strict';

            $scope.instanceId = instanceId;
            $scope.fileContent = '';
            $scope.selection = { file: undefined };
            $scope.files = [];

            /** Load the log files */
            AdminBatchService.getBatchLogFiles($scope.instanceId)
                .success(function (fileNames) {
                    $scope.files = fileNames;
                });

            $scope.reloadLogFile = function () {
                var file = $scope.selection.file;
                if (file && file.length > 0) {
                    AdminBatchService.getBatchLogFileContent($scope.instanceId, file)
                        .success(function (fileContent) {
                            $scope.fileContent = fileContent;
                        })
                        .error(function (err) {
                            $scope.fileContent = err;
                        });
                } else {
                    $scope.fileContent = '';
                }
            };

            $scope.$watch("selection.file", $scope.reloadLogFile, true);
        }])


    /**
     * ********************************************************************************
     * DictionariesAdminCtrl
     * ********************************************************************************
     * Dictionaries Admin Controller
     * Controller for the Dictionaries settings page
     */
    .controller('DictionariesAdminCtrl', ['$scope', 'growl', 'AdminDictionariesService',
        function ($scope, growl, AdminDictionariesService) {
            'use strict';

            $scope.search = '';
            $scope.dictionary = [];
            $scope.entry = undefined; // The entry being edited


            /** Loads the settings from the back-end */
            $scope.loadDictionaries = function() {
                $scope.entry = undefined;
                AdminDictionariesService
                    .getDictionary('web')
                    .success(function (dictionary) {
                        $scope.dictionary = dictionary;
                    });
            };


            /** Edits an entry **/
            $scope.editEntry = function (entry) {
                $scope.entry = angular.copy(entry);
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving entry", { ttl: 5000 });
            };


            /** Saves the current entry being edited */
            $scope.saveEntry = function () {
                if ($scope.entry) {
                    AdminDictionariesService
                        .updateEntry($scope.dictionary, $scope.entry)
                        .success($scope.loadDictionaries)
                        .error($scope.displayError);
                }
            };

        }])


    /**
     * ********************************************************************************
     * SettingsAdminCtrl
     * ********************************************************************************
     * Settings Admin Controller
     * Controller for the Admin settings page
     */
    .controller('SettingsAdminCtrl', ['$scope', 'growl', 'AdminSettingsService', 'UploadFileService',
        function ($scope, growl, AdminSettingsService, UploadFileService) {
            'use strict';

            $scope.search = '';
            $scope.settings = [];
            $scope.setting = undefined; // The setting being edited


            /** Loads the settings from the back-end */
            $scope.loadSettings = function() {
                $scope.setting = undefined;
                AdminSettingsService
                    .getEditableSettings()
                    .success(function (settings) {
                        $scope.settings = settings;
                    });
            };


            /** Edits a setting **/
            $scope.editSetting = function (setting) {
                $scope.setting = angular.copy(setting);
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving setting", { ttl: 5000 });
            };


            /** Saves the current setting being edited */
            $scope.saveSetting = function () {
                if ($scope.setting) {
                    AdminSettingsService
                        .updateSetting($scope.setting)
                        .success($scope.loadSettings)
                        .error($scope.displayError);
                }
            };

            /** Download the instance data file */
            $scope.exportSettings = function () {
                AdminSettingsService
                    .getSettingsExportTicket()
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/settings/editable-settings?ticket=' + ticket;
                        link.click();
                    });
            };

            /** Opens the upload-domains dialog **/
            $scope.uploadSettingsDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Settings File',
                    '/rest/settings/upload-settings',
                    'json');
            };

        }]);
