
/**
 * The message list service
 */
angular.module('niord.messages')

    /**
     * Interface for calling the application server
     */
    .factory('FilterService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {

            getFilters: function(success, error) {
                $http.get('/rest/filters/all')
                    .success(success)
                    .error(error);
            },

            addFilter: function(filter, success, error) {
                $http.post('/rest/filters/', filter)
                    .success(success)
                    .error(error);
            },

            removeFilter: function(filterId, success, error) {
                $http.delete('/rest/filters/' + filterId)
                    .success(success)
                    .error(error);
            }

        };
    }]);

