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
            $scope.count = 10;
            $scope.searchResult = {
                total: 0,
                size: 0,
                data: []
            };


            // Loads the batch status
            $scope.loadBatchStatus = function () {
                AdminService.getBatchStatus()
                    .success(function (status) {
                        $scope.batchStatus = status;
                        $scope.loadBatchInstances();
                    });
            };


            // Loads "count" more batch instances
            $scope.loadBatchInstances = function () {
                if ($scope.batchName) {
                    AdminService.getBatchInstances($scope.batchName, $scope.searchResult.size, $scope.count)
                        .success(function (result) {
                            $.merge($scope.searchResult.data, result.data);
                            $scope.searchResult.total = result.total;
                            $scope.searchResult.size = $scope.searchResult.data.length;
                        });
                }
            };


            // Called when the given batch type is displayed
            $scope.initBatchType = function (name) {
                $scope.batchName = name;
                $scope.searchResult = {
                    total: 0,
                    size: 0,
                    data: []
                };
                $scope.loadBatchInstances();
            };

        }]);
