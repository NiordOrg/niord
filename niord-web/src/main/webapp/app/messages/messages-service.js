
/**
 * The message list service
 */
angular.module('niord.messages')

    /**
     * Interface for calling the application server
     */
    .factory('FilterService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns the message filters */
            getFilters: function() {
                return $http.get('/rest/filters/all');
            },


            /** Adds a new message filter */
            addFilter: function(filter) {
                return $http.post('/rest/filters/', filter);
            },


            /** Removes hte message filter with the given ID */
            removeFilter: function(filterId) {
                return $http.delete('/rest/filters/' + filterId);
            }

        };
    }]);

