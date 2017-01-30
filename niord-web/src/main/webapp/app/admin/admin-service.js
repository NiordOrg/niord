
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
            },

            generateFiringAreaMessageTemplates: function(seriesId, tagId) {
                return $http.post('/rest/firing-schedules/generate-firing-area-messages',
                    { seriesId: seriesId, tagId: tagId });
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
            searchPublications: function (filter, page) {
                var params = 'lang=' + $rootScope.language;
                if ($rootScope.domain) {
                    params += '&domain=' + encodeURIComponent($rootScope.domain.domainId);
                }
                if (filter.title) {
                    params += '&title=' + encodeURIComponent(filter.title);
                }
                if (filter.mainType && filter.mainType.length > 0) {
                    params += '&mainType=' + filter.mainType;
                }
                if (filter.type && filter.type.length > 0) {
                    params += '&type=' + filter.type;
                }
                if (filter.status && filter.status.length > 0) {
                    params += '&status=' + filter.status;
                }
                if (filter.category && filter.category.length > 0) {
                    params += '&category=' + encodeURIComponent(filter.category);
                }
                if (filter.maxSize) {
                    params += '&maxSize=' + filter.maxSize;
                }
                if (page) {
                    params += '&page=' + (page - 1);
                }
                return $http.get('/rest/publications/search-details?' + params);
            },


            /** Returns the details for the given publication **/
            getPublicationDetails: function (publication) {
                return $http.get('/rest/publications/editable-publication/' + publication.publicationId);
            },


            /** Returns the new publication template **/
            newPublicationTemplate: function (mainType) {
                return $http.get('/rest/publications/new-publication-template?mainType=' + mainType);
            },


            /** Returns the new publication template **/
            copyPublicationTemplate: function (publication, nextIssue) {
                return $http.get('/rest/publications/copy-publication-template/'
                    + encodeURIComponent(publication.publicationId)
                    + '?nextIssue=' + nextIssue);
            },


            /** Creates a new publication **/
            createPublication: function(publication) {
                return $http.post('/rest/publications/publication/', publication);
            },


            /** Updates the given publication **/
            updatePublication: function(publication) {
                return $http.put('/rest/publications/publication/' + publication.publicationId, publication);
            },


            /** Updates the status of the given publication **/
            updatePublicationStatus: function (publication, status) {
                return $http.put('/rest/publications/update-status', {
                    publicationId: publication.publicationId, status: status });
            },


            /** Deletes the given publication **/
            deletePublication: function(publication) {
                return $http['delete']('/rest/publications/publication/' + publication.publicationId);
            },


            /** Deletes the given publication file  **/
            deletePublicationFile: function (path) {
                return $http['delete'](path);
            },


            /** Generates a new publication report **/
            generatePublicationReport: function (desc, repoPath, printParam) {
                return $http.post(
                    '/rest/publications/generate-publication-report/' + encodeURIComponent(repoPath) + '?' + printParam,
                    desc);
            },


            /** Returns the ticket that can be used to generate an export file that requires the amdin role */
            publicationTicket: function (role) {
                var param = role ? '?role=' + role : '';
                return $http.get('/rest/tickets/ticket' + param);
            },


            /** Returns all publication categories **/
            getPublicationCategories: function () {
                return $http.get('/rest/publication-categories/all?lang=' + $rootScope.language);
            },


            /** Returns the details for the given publication category **/
            getPublicationCategoryDetails: function (publicationCategory) {
                return $http.get('/rest/publication-categories/publication-category/' + publicationCategory.categoryId);
            },


            /** Creates a new publication category **/
            createPublicationCategory: function(publicationCategory) {
                return $http.post('/rest/publication-categories/publication-category/', publicationCategory);
            },


            /** Updates the given publication category **/
            updatePublicationCategory: function(publicationCategory) {
                return $http.put('/rest/publication-categories/publication-category/' + publicationCategory.categoryId, publicationCategory);
            },


            /** Deletes the given publication category **/
            deletePublicationCategory: function(publicationCategory) {
                return $http['delete']('/rest/publication-categories/publication-category/' + publicationCategory.categoryId);
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
                return $http.get('/rest/domains/all?inactive=true&lang=' + $rootScope.language);
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
     * AdminScheduleService
     * ********************************************************************************
     * Interface for calling firing schedule related functions at the application server
     */
    .factory('AdminScheduleService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {

            /** Returns all schedules **/
            getFiringSchedules: function () {
                return $http.get('/rest/firing-schedules/all?lang=' + $rootScope.language);
            },


            /** Creates a new schedule **/
            createFiringSchedule: function(schedule) {
                return $http.post('/rest/firing-schedules/firing-schedule/', schedule);
            },


            /** Updates the given schedule **/
            updateFiringSchedule: function(schedule) {
                return $http.put('/rest/firing-schedules/firing-schedule/' + schedule.id, schedule);
            },


            /** Deletes the given schedule **/
            deleteFiringSchedule: function(schedule) {
                return $http['delete']('/rest/firing-schedules/firing-schedule/' + schedule.id);
            },


            /** Updates the firing exercises based on active schedules **/
            updateFiringExercises: function () {
                return $http.put('/rest/firing-schedules/update-firing-exercises');
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
            },

            /** Executes the given JavaScript on the back-end (only Sysadmins) **/
            executeJavaScript: function (scritpName, javascript) {
                return $http.post('/rest/batch/execute-javascript', {
                    scriptName: scritpName,
                    javaScript: javascript
                });
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
            },

            /** Reloads dictionaries from resource bundles **/
            reloadDictionaries: function() {
                return $http.put('/rest/dictionaries/reload-resource-bundles');
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

