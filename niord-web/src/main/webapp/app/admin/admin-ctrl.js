/**
 * The admin controllers.
 */
angular.module('niord.admin')

    /**
     * Admin Controller
     * Will periodically reload batch status to display the number of running batch jobs in the admin page menu
     */
    .controller('CommonAdminCtrl', ['$scope', '$interval', 'AdminService',
        function ($scope, $interval, AdminService) {
            'use strict';

            $scope.batchStatus = {
                runningExecutions: 0,
                types: []
            };

            // Loads the batch status
            $scope.loadBatchStatus = function () {
                AdminService.getBatchStatus()
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
     * Batch Admin Controller
     */
    .controller('BatchAdminCtrl', ['$scope', '$interval', '$routeParams', 'AdminService',
        function ($scope, $interval, $routeParams, AdminService) {
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
                AdminService.getBatchStatus()
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
                    AdminService.getBatchInstances($scope.batchName, $scope.currentPage - 1, $scope.pageSize)
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
                AdminService
                    .stopBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };

            // Restarts the given batch job
            $scope.restart = function (execution) {
                AdminService
                    .restartBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };

            // Abandon the given batch job
            $scope.abandon = function (execution) {
                AdminService
                    .abandonBatchExecution(execution.executionId)
                    .success($scope.loadBatchInstances);
            };

            // Download the instance data file
            $scope.download = function (instance) {
                AdminService
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
            }

        }]);
