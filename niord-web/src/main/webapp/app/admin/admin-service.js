
/**
 * The Admin service
 */
angular.module('niord.admin')

    /**
     * Interface for calling chart related functions at the application server
     */
    .factory('AdminChartService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns all charts **/
            getCharts: function () {
                return $http.get('/rest/charts/all');
            },


            /** Creates a new chart **/
            createChart: function(chart) {
                return $http.post('/rest/charts/chart', chart);
            },


            /** Updates the given chart **/
            updateChart: function(chart) {
                return $http.put('/rest/charts/chart/' + encodeURIComponent(chart.chartNumber), chart);
            },


            /** Deletes the given chart **/
            deleteChart: function(chart) {
                return $http.delete('/rest/charts/chart/' + encodeURIComponent(chart.chartNumber));
            }

        };
    }])


    /**
     * Interface for calling batch-related functions at the application server
     */
    .factory('AdminBatchService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns the batch system status **/
            getBatchStatus: function() {
                return $http.get('/rest/batch/status');
            },

            /** Returns a page full of batch job instances **/
            getBatchInstances: function (name, page, pageSize) {
                var url = '/rest/batch/' + name + '/instances';
                if (pageSize) {
                    url += '?page=' + page + '&pageSize=' + pageSize;
                }
                return $http.get(url);
            },

            /** Stops the given batch execution **/
            stopBatchExecution: function(executionId) {
                return $http.put('/rest/batch/execution/' + executionId + '/stop');
            },

            /** Stops the given batch execution **/
            restartBatchExecution: function(executionId) {
                return $http.put('/rest/batch/execution/' + executionId + '/restart');
            },

            /** Stops the given batch execution **/
            abandonBatchExecution: function(executionId) {
                return $http.put('/rest/batch/execution/' + executionId + '/abandon');
            },

            getBatchDownloadTicket: function () {
                return $http.get('/rest/batch/download-ticket');
            },

            getBatchLogFiles: function (instanceId) {
                return $http.get('/rest/batch/instance/' + instanceId + '/logs');
            },

            getBatchLogFileContent: function (instanceId, logFileName, fromLineNo) {
                var fromLine = fromLineNo ? '?fromLineNo=' + fromLineNo : '';
                return $http.get('/rest/batch/instance/' + instanceId + '/logs/' + encodeURIComponent(logFileName) + fromLine);
            }

        };
    }]);

