/*
 * Copyright 2017 Danish Maritime Authority.
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
     * BatchAdminCtrl
     * ********************************************************************************
     * Batch Admin Controller
     */
    .controller('BatchAdminCtrl', ['$scope', '$interval', '$stateParams', '$uibModal', 'growl', 'AdminBatchService', 'UploadFileService',
        function ($scope, $interval, $stateParams, $uibModal, growl, AdminBatchService, UploadFileService) {
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
                        if (index === 0) {
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
                    templateUrl: "/app/sysadmin/batch-log-file-dialog.html",
                    size: 'l',
                    resolve: {
                        instanceId: function () {
                            return instanceId;
                        }
                    }
                });
            };


            /** Opens the upload-batch-set dialog **/
            $scope.uploadBatchSet = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload batch-set zip archive',
                    '/rest/batch/execute-batch-set',
                    'zip');
            };


            /** Allows the user (only sysadmins!) to execute a JavaScript on the back-end **/
            $scope.executeJavaScript = function () {
                $uibModal.open({
                    controller: function ($scope, $timeout) {
                        $scope.javaScript = '';
                        $scope.jsEditorOptions = {
                            useWrapMode : false,
                            showGutter: true,
                            mode: 'javascript',
                            onLoad: function(editor) {
                                $scope.editor = editor;
                                editor.$blockScrolling = 1;
                            }
                        };
                        $timeout(function () {
                            try { $scope.editor.focus(); } catch (e) {}
                        }, 100)
                    },
                    templateUrl: "javaScriptBatchJobDialog.html",
                    size: 'lg'
                }).result.then(function (javascript) {
                    if (javascript && javascript.length > 0) {
                        AdminBatchService.executeJavaScript('javascript.js', javascript)
                            .success(function () {
                                growl.info('Scheduled script-executor batch job', { ttl: 3000 });
                            })
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
        }]);

