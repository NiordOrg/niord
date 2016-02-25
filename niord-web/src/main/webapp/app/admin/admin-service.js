
/**
 * The Admin service
 */
angular.module('niord.admin')

    /**
     * Interface for calling the application server
     */
    .factory('AdminService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns the batch system status **/
            getBatchStatus: function() {
                return $http.get('/rest/batch/status');
            },

            /** Returns a page full of batch job instances **/
            getBatchInstances: function (name, start, count) {
                var url = '/rest/batch/' + name + '/instances';
                if (count) {
                    url += '?start=' + start + '&count=' + count;
                }
                return $http.get(url);
            }

        };
    }]);

