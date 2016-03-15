/**
 * The admin controllers.
 */
angular.module('niord.admin')

    /**
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
     * Charts Admin Controller
     * Controller for the Admin Charts page
     */
    .controller('ChartsAdminCtrl', ['$scope', 'growl', 'AdminChartService', 'DialogService',
        function ($scope, growl, AdminChartService, DialogService) {
            'use strict';

            $scope.allCharts = [];
            $scope.chart = undefined; // The chart being edited
            $scope.editMode = 'add';

            // Pagination
            $scope.charts = [];
            $scope.pageSize = 20;
            $scope.currentPage = 1;
            $scope.chartNo = 0;
            $scope.search = '';


            /** Loads the charts from the back-end */
            $scope.loadCharts = function() {
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
                $scope.chart = { chartNumber: undefined, internationalNumber: undefined, horizontalDatum: undefined };
            };


            /** Edits a chart **/
            $scope.editChart = function (chart) {
                $scope.editMode = 'edit';
                $scope.chart = angular.copy(chart);
            };


            /** Cancel adding/editing a chart **/
            $scope.cancelEditChart = function () {
                $scope.editMode = undefined;
                $scope.chart = undefined;
            };


            /** Displays the error message */
            $scope.displayError = function (error) {
                growl.error("Error: " + error);
            };


            /** Saves the current chart being edited */
            $scope.saveChart = function () {
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
            }

        }])



    /**
     * Batch Admin Controller
     */
    .controller('BatchAdminCtrl', ['$scope', '$interval', '$routeParams', '$uibModal', 'AdminBatchService',
        function ($scope, $interval, $routeParams, $uibModal, AdminBatchService) {
            'use strict';

            $scope.batchStatus = {
                runningExecutions: 0,
                types: []
            };
            $scope.batchName = $routeParams.batchName;
            $scope.pageSize = 5;
            $scope.currentPage = 1;
            $scope.executions = [];
            $scope.searchResult = {
                total: 0,
                size: 0,
                data: []
            };


            // Build a flat list of executions of all batch instances.
            // This will make it easier to present the result in a table
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

            // Loads the batch status
            $scope.loadBatchStatus = function () {
                AdminBatchService.getBatchStatus()
                    .success(function (status) {
                        $scope.batchStatus = status;
                        $scope.loadBatchInstances();
                    });
            };


            // Refresh batch status every 3 seconds
            var refreshInterval = $interval($scope.loadBatchStatus, 3 * 1000);

            // Terminate the timer
            $scope.$on("$destroy", function() {
                if (refreshInterval) {
                    $interval.cancel(refreshInterval);
                }
            });


            // Loads "count" more batch instances
            $scope.loadBatchInstances = function () {
                if ($scope.batchName) {
                    AdminBatchService.getBatchInstances($scope.batchName, $scope.currentPage - 1, $scope.pageSize)
                        .success(function (result) {
                            $scope.searchResult = result;
                            $scope.buildBatchExecutionList();
                        });
                }
            };


            // Called when the given batch type is displayed
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

            /**
             * public enum BatchStatus {STARTING, STARTED, STOPPING,
			STOPPED, FAILED, COMPLETED, ABANDONED }
             */

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

            // Stops the given batch job
            $scope.stop = function (execution) {
                AdminBatchService
                    .stopBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };

            // Restarts the given batch job
            $scope.restart = function (execution) {
                AdminBatchService
                    .restartBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };

            // Abandon the given batch job
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

            // Open the logs dialo
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
     * Dialog Controller for the Batch job log file dialog
     */
    .controller('BatchLogFileDialogCtrl', ['$scope', 'AdminBatchService', 'instanceId',
        function ($scope, AdminBatchService, instanceId) {
            'use strict';

            $scope.instanceId = instanceId;
            $scope.fileContent = '';
            $scope.selection = { file: undefined };
            $scope.files = [];

            // Load the log files
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
        }]);
