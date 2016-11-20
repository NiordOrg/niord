
/*
 * Copyright 2016 Danish Maritime Authority.
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
 * The Admin service
 */
angular.module('niord.admin')

    /**
     * ********************************************************************************
     * AdminChartService
     * ********************************************************************************
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
                return $http.post('/rest/charts/chart/', chart);
            },


            /** Updates the given chart **/
            updateChart: function(chart) {
                return $http.put('/rest/charts/chart/' + encodeURIComponent(chart.chartNumber), chart);
            },


            /** Deletes the given chart **/
            deleteChart: function(chart) {
                return $http['delete']('/rest/charts/chart/' + encodeURIComponent(chart.chartNumber));
            }

        };
    }])


    /**
     * ********************************************************************************
     * AdminAreaService
     * ********************************************************************************
     * Interface for calling area-related functions at the application server
     */
    .factory('AdminAreaService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {
            getAreas: function() {
                return $http.get('/rest/areas/area-roots?lang=' + $rootScope.language);
            },

            getArea: function(area, includeParents) {
                var params = includeParents ? '?parent=true' : '';
                return $http.get('/rest/areas/area/' + area.id + params);
            },

            createArea: function(area) {
                return $http.post('/rest/areas/area/', area);
            },

            updateArea: function(area) {
                return $http.put('/rest/areas/area/' + area.id, area);
            },

            deleteArea: function(area) {
                return $http['delete']('/rest/areas/area/' + area.id);
            },

            moveArea: function(areaId, parentId) {
                return $http.put('/rest/areas/move-area', { areaId: areaId, parentId: parentId });
            },

            changeSortOrder: function(areaId, moveUp) {
                return $http.put('/rest/areas/change-sort-order', { areaId: areaId, moveUp: moveUp });
            },

            recomputeTreeSortOrder: function() {
                return $http.put('/rest/areas/recompute-tree-sort-order');
            },

            recomputePublishedMessagesSortOrder: function() {
                return $http.put('/rest/messages/recompute-area-sort-order');
            }

        };
    }])


    /**
     * ********************************************************************************
     * AdminCategoryService
     * ********************************************************************************
     * Interface for calling category-related functions at the application server
     */
    .factory('AdminCategoryService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {
            getCategories: function() {
                return $http.get('/rest/categories/category-roots?lang=' + $rootScope.language);
            },

            getCategory: function(category) {
                return $http.get('/rest/categories/category/' + category.id);
            },

            createCategory: function(category) {
                return $http.post('/rest/categories/category/', category);
            },

            updateCategory: function(category) {
                return $http.put('/rest/categories/category/' + category.id, category);
            },

            deleteCategory: function(category) {
                return $http['delete']('/rest/categories/category/' + category.id);
            },

            moveCategory: function(categoryId, parentId) {
                return $http.put('/rest/categories/move-category', { categoryId: categoryId, parentId: parentId });
            }
        };
    }])


    /**
     * ********************************************************************************
     * AdminPublicationService
     * ********************************************************************************
     * Interface for calling publication related functions at the application server
     */
    .factory('AdminPublicationService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {

            /** Returns all publications **/
            getPublications: function () {
                return $http.get('/rest/publications/all?lang=' + $rootScope.language);
            },


            /** Returns the details for the given publication **/
            getPublicationDetails: function (publication) {
                return $http.get('/rest/publications/publication/' + publication.publicationId);
            },


            /** Creates a new publication **/
            createPublication: function(publication) {
                return $http.post('/rest/publications/publication/', publication);
            },


            /** Updates the given publication **/
            updatePublication: function(publication) {
                return $http.put('/rest/publications/publication/' + publication.publicationId, publication);
            },


            /** Deletes the given publication **/
            deletePublication: function(publication) {
                return $http['delete']('/rest/publications/publication/' + publication.publicationId);
            }

        };
    }])


    /**
     * ********************************************************************************
     * AdminSourceService
     * ********************************************************************************
     * Interface for calling source related functions at the application server
     */
    .factory('AdminSourceService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {

            /** Returns all sources **/
            getSources: function () {
                return $http.get('/rest/sources/all?lang=' + $rootScope.language);
            },


            /** Returns the details for the given source **/
            getSourceDetails: function (source) {
                return $http.get('/rest/sources/source/' + source.id);
            },


            /** Creates a new source **/
            createSource: function(source) {
                return $http.post('/rest/sources/source/', source);
            },


            /** Updates the given source **/
            updateSource: function(source) {
                return $http.put('/rest/sources/source/' + source.id, source);
            },


            /** Deletes the given source **/
            deleteSource: function(source) {
                return $http['delete']('/rest/sources/source/' + source.id);
            }

        };
    }])


    /**
     * ********************************************************************************
     * AdminUserService
     * ********************************************************************************
     * Interface for calling user and group-membership functions at the application server
     */
    .factory('AdminUserService', [ '$http', function($http) {
        'use strict';

        return {
            /** Returns the Keycloak users matching the search parameters **/
            getUsers: function(filter, first, max) {
                filter = filter || '';
                first = first || 0;
                max = max || 20;
                return $http.get('/rest/users/kc-users?search=' + encodeURIComponent(filter)
                            + '&first=' + first + '&max=' + max);
            },


            /** Adds the given user to Keycloak  **/
            addUser: function(user) {
                return $http.post('/rest/users/kc-user/', user);
            },


            /** Updates the given user in Keycloak  **/
            updateUser: function(user) {
                return $http.put('/rest/users/kc-user/' + encodeURIComponent(user.keycloakId), user);
            },


            /** Deletes the given user in Keycloak  **/
            deleteUser: function(userId) {
                return $http['delete']('/rest/users/kc-user/' + encodeURIComponent(userId));
            },


            /** Returns the Keycloak groups **/
            getGroups: function() {
                return $http.get('/rest/users/kc-groups');
            },


            /** Returns the roles  **/
            getUserGroups: function(userId) {
                return $http.get('/rest/users/kc-user/' + encodeURIComponent(userId) + '/kc-groups');
            },


            /** Let the user join the given group  **/
            joinUserGroup: function(userId, groupId) {
                return $http.put('/rest/users/kc-user/' + encodeURIComponent(userId)
                                + '/kc-groups/'+ encodeURIComponent(groupId));
            },


            /** Let the user leave the given group  **/
            leaveUserGroup: function(userId, groupId) {
                return $http['delete']('/rest/users/kc-user/' + encodeURIComponent(userId)
                    + '/kc-groups/'+ encodeURIComponent(groupId));
            }
        };
    }])


    /**
     * ********************************************************************************
     * AdminMessageSeriesService
     * ********************************************************************************
     * Interface for calling message series related functions at the application server
     */
    .factory('AdminMessageSeriesService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns all message series **/
            getMessageSeries: function () {
                return $http.get('/rest/message-series/all?messageNumbers=true');
            },


            /** Creates a new message series **/
            createMessageSeries: function(series) {
                return $http.post('/rest/message-series/series/', series);
            },


            /** Updates the given message series **/
            updateMessageSeries: function(series) {
                return $http.put('/rest/message-series/series/' + encodeURIComponent(series.seriesId), series);
            },


            /** Deletes the given message series **/
            deleteMessageSeries: function(series) {
                return $http['delete']('/rest/message-series/series/' + encodeURIComponent(series.seriesId));
            },


            /** Returns the next message series number for the given year **/
            getNextMessageSeriesNumber: function (seriesId, year) {
                return $http.get('/rest/message-series/series/' + encodeURIComponent(seriesId) + '/number/' + year);
            },


            /** Sets the next message series number for the given year **/
            updateNextMessageSeriesNumber: function (seriesId, year, num) {
                return $http.put('/rest/message-series/series/' + encodeURIComponent(seriesId) + '/number/' + year, num);
            },


            /** Computes the next message series number for the given year **/
            computeNextMessageSeriesNumber: function (seriesId, year) {
                return $http.get('/rest/message-series/series/' + encodeURIComponent(seriesId) + '/compute-number/' + year);
            }
        };
    }])


    /**
     * ********************************************************************************
     * AdminDomainService
     * ********************************************************************************
     * Interface for calling domain related functions at the application server
     */
    .factory('AdminDomainService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {

            /** Returns all domain **/
            getDomains: function () {
                return $http.get('/rest/domains/all?lang=' + $rootScope.language);
            },


            /** Creates a new domain **/
            createDomain: function(domain) {
                return $http.post('/rest/domains/domain/', domain);
            },


            /** Updates the given domain **/
            updateDomain: function(domain) {
                return $http.put('/rest/domains/domain/' + encodeURIComponent(domain.domainId), domain);
            },


            /** Deletes the given domain **/
            deleteDomain: function(domain) {
                return $http['delete']('/rest/domains/domain/' + encodeURIComponent(domain.domainId));
            },


            /** Creates the domain in Keycloak **/
            createDomainInKeycloak : function (domain) {
                return $http.post('/rest/domains/keycloak', domain);
            }
        };
    }])


    /**
     * ********************************************************************************
     * AdminBatchService
     * ********************************************************************************
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
                return $http.get('/rest/tickets/ticket?role=admin');
            },

            getBatchLogFiles: function (instanceId) {
                return $http.get('/rest/batch/instance/' + instanceId + '/logs');
            },

            getBatchLogFileContent: function (instanceId, logFileName, fromLineNo) {
                var fromLine = fromLineNo ? '?fromLineNo=' + fromLineNo : '';
                return $http.get('/rest/batch/instance/' + instanceId + '/logs/' + encodeURIComponent(logFileName) + fromLine);
            }

        };
    }])


    /**
     * ********************************************************************************
     * AdminDictionariesService
     * ********************************************************************************
     * Interface for calling dictionaries-related functions at the application server
     */
    .factory('AdminDictionariesService', [ '$http', function($http) {
        'use strict';

        return {
            getDictionaryNames: function() {
                return $http.get('/rest/dictionaries/names');
            },

            getDictionaryEntries: function(name) {
                return $http.get('/rest/dictionaries/dictionary/' + encodeURIComponent(name) + '/entries');
            },

            addEntry: function(name, entry) {
                return $http.post('/rest/dictionaries/dictionary/' + encodeURIComponent(name), entry);
            },

            updateEntry: function(name, entry) {
                return $http.put('/rest/dictionaries/dictionary/' + encodeURIComponent(name) + '/'
                    + encodeURIComponent(entry.key), entry);
            },

            deleteEntry: function(name, entry) {
                return $http['delete']('/rest/dictionaries/dictionary/' + encodeURIComponent(name) + '/'
                    + encodeURIComponent(entry.key));
            }
        };
    }])


    /**
     * ********************************************************************************
     * AdminSettingsService
     * ********************************************************************************
     * Interface for calling system settings-related functions at the application server
     */
    .factory('AdminSettingsService', [ '$http', function($http) {
        'use strict';

        return {
            getEditableSettings: function() {
                return $http.get('/rest/settings/editable-settings');
            },

            updateSetting: function(setting) {
                return $http.put('/rest/settings/setting/' + setting.key, setting);
            },

            getSettingsExportTicket: function () {
                return $http.get('/rest/tickets/ticket?role=sysadmin');
            }

        };
    }]);

