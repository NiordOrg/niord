
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


            /** Releases a recoding message-report based publication **/
            releasePublication: function (publication, nextIssue) {
                return $http.put('/rest/publications/release-publication/' + publication.publicationId
                    + '?nextIssue=' + nextIssue);
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
     * AdminContactService
     * ********************************************************************************
     * Interface for calling contact functions at the application server
     */
    .factory('AdminContactService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns the contacts matching the search parameters **/
            searchContacts: function(params, page) {
                var p = 'maxSize=' + page.maxSize + '&page=' + (page.page - 1);
                if (params.name && params.name.length > 0) {
                    p += '&name=' + encodeURIComponent(params.name);
                }
                return $http.get('/rest/contacts/search?' + p);
            },


            /** Creates the given contact  **/
            createContact: function(contact) {
                return $http.post('/rest/contacts/contact/', contact);
            },


            /** Updates the given contact **/
            updateContact: function(contact) {
                return $http.put('/rest/contacts/contact/' + encodeURIComponent(contact.id), contact);
            },


            /** Deletes the given contact **/
            deleteContact: function(contact) {
                return $http['delete']('/rest/contacts/contact/' + contact.id);
            },


            /** Returns the ticket that can be used to generate an export file that requires the admin role */
            exportTicket: function (role) {
                var param = role ? '?role=' + role : '';
                return $http.get('/rest/tickets/ticket' + param);
            },


            /** Imports a comma-separated list of emails as new contacts **/
            importContactEmails: function (emails) {
                return $http.post('/rest/contacts/import-emails', { emails: emails });
            }
        };
    }])


    /**
     * ********************************************************************************
     * AdminMailingListService
     * ********************************************************************************
     * Interface for calling mailing list functions at the application server
     */
    .factory('AdminMailingListService', [ '$rootScope', '$http', function($rootScope, $http) {
        'use strict';

        return {

            /** Returns all the mailing lists **/
            getMailingLists: function() {
                return $http.get('/rest/mailing-lists/search?lang=' + $rootScope.language);
            },


            /** Returns the details of the given mailing list ID **/
            getMailingList: function(mailingListId) {
                return $http.get('/rest/mailing-lists/mailing-list/' + encodeURIComponent(mailingListId));
            },


            /** Creates the given mailing list  **/
            createMailingList: function(mailingList) {
                return $http.post('/rest/mailing-lists/mailing-list/', mailingList);
            },


            /** Updates the given mailing list **/
            updateMailingList: function(mailingList) {
                return $http.put('/rest/mailing-lists/mailing-list/'
                    + encodeURIComponent(mailingList.mailingListId), mailingList);
            },


            /** Deletes the given mailing list **/
            deleteMailingList: function(mailingList) {
                return $http['delete']('/rest/mailing-lists/mailing-list/'
                    + encodeURIComponent(mailingList.mailingListId));
            },


            /** Returns the selected and available users for the mailing list **/
            getRecipientUsers: function(mailingList) {
                return $http.get('/rest/mailing-lists/mailing-list/'
                    + encodeURIComponent(mailingList.mailingListId) + '/users');
            },


            /** Updates the recipient users for the mailing list **/
            updateRecipientUsers: function(mailingList, users) {
                return $http.put('/rest/mailing-lists/mailing-list/'
                    + encodeURIComponent(mailingList.mailingListId) + '/users', users);
            },


            /** Returns the selected and available contacts for the mailing list **/
            getRecipientContacts: function(mailingList) {
                return $http.get('/rest/mailing-lists/mailing-list/'
                    + encodeURIComponent(mailingList.mailingListId) + '/contacts');
            },


            /** Updates the recipient contacts for the mailing list **/
            updateRecipientContacts: function(mailingList, contacts) {
                return $http.put('/rest/mailing-lists/mailing-list/'
                    + encodeURIComponent(mailingList.mailingListId) + '/contacts', contacts);
            }

        };
    }]);

