
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
 * The Admin service
 */
angular.module('niord.admin')


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
     * AdminPromulgationService
     * ********************************************************************************
     * Interface for calling promulgation related functions at the application server
     */
    .factory('AdminPromulgationService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns all promulgation services **/
            getPromulgationServices: function () {
                return $http.get('/rest/promulgations/promulgation-services/all');
            },


            /** Returns all promulgation types **/
            getPromulgationTypes: function () {
                return $http.get('/rest/promulgations/promulgation-types/all');
            },


            /** Creates a new  promulgation type **/
            createPromulgationType: function(promulgationType) {
                return $http.post('/rest/promulgations/promulgation-type/', promulgationType);
            },


            /** Updates the given promulgation type **/
            updatePromulgationType: function(promulgationType) {
                return $http.put('/rest/promulgations/promulgation-type/'
                    + encodeURIComponent(promulgationType.typeId), promulgationType);
            },


            /** Deletes the given promulgation type **/
            deletePromulgationType: function(promulgationType) {
                return $http['delete']('/rest/promulgations/promulgation-type/'
                    + encodeURIComponent(promulgationType.typeId));
            },

            /** Returns the ticket that can be used to generate an export file that requires the given role */
            exportTicket: function (role) {
                var param = role ? '?role=' + role : '';
                return $http.get('/rest/tickets/ticket' + param);
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
     * AdminScriptResourceService
     * ********************************************************************************
     * Interface for calling script resource-related functions at the application server
     */
    .factory('AdminScriptResourceService', [ '$http', '$uibModal', function($http, $uibModal) {
        'use strict';

        return {

            /** Returns all script resources **/
            getScriptResources: function (type) {
                var params = type !== undefined ? '?type=' + type : '';
                return $http.get('/rest/script-resources/all' + params);
            },


            /** Creates a new script resource **/
            createScriptResource: function(template) {
                return $http.post('/rest/script-resources/script-resource/', template);
            },


            /** Updates the given script resource **/
            updateScriptResource: function(template) {
                return $http.put('/rest/script-resources/script-resource/' + template.id, template);
            },


            /** Deletes the given script resource **/
            deleteScriptResource: function(template) {
                return $http['delete']('/rest/script-resources/script-resource/' + template.id);
            },


            /** Reload script resources from the file system **/
            reloadScriptResources: function() {
                return $http.post('/rest/script-resources/reload/');
            },

            /** Loads the script resource history for the given script resource **/
            getScriptResourceHistory: function(template) {
                return $http.get('/rest/script-resources/script-resource/' + template.id + '/history');
            },


            /** Returns the ticket that can be used to generate an export file that requires the given role */
            exportTicket: function (role) {
                var param = role ? '?role=' + role : '';
                return $http.get('/rest/tickets/ticket' + param);
            },


            /** Opens a dialog for selecting a script resource **/
            scriptResourceDialog : function (type) {
                return $uibModal.open({
                    controller: "ScriptResourceDialogCtrl",
                    templateUrl: "/app/sysadmin/script-resource-dialog.html",
                    size: 'md',
                    resolve: {
                        type: function () { return type; }
                    }
                });
            }

        };
    }])


    /**
     * ********************************************************************************
     * AdminParamTypeService
     * ********************************************************************************
     * Interface for calling domain related functions at the application server
     */
    .factory('AdminParamTypesService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {

            /** Returns all parameter types **/
            getParamTypes: function () {
                return $http.get('/rest/templates/parameter-types?lang=' + $rootScope.language);
            },


            /** Returns the parameter type details for the given ID **/
            getParamTypeDetails: function (id) {
                return $http.get('/rest/templates/parameter-type/' + id);
            },


            /** Creates a new parameter types **/
            createParamType: function(paramType) {
                return $http.post('/rest/templates/parameter-type/', paramType);
            },


            /** Updates the given parameter types **/
            updateParamType: function(paramType) {
                return $http.put('/rest/templates/parameter-type/' + paramType.id, paramType);
            },


            /** Deletes the given parameter types **/
            deleteParamType: function(paramType) {
                return $http['delete']('/rest/templates/parameter-type/' + paramType.id);
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
            },

            /** Executes the message template on the given message ID **/
            executeCategoryTemplate: function(category, messageId) {
                return $http.put('/rest/templates/execute', { messageId: encodeURIComponent(messageId), category: category });
            }
        };
    }])


    /**
     * ********************************************************************************
     * AdminReportService
     * ********************************************************************************
     * Interface for calling report related functions at the application server
     */
    .factory('AdminReportService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns all reports **/
            getReports: function () {
                return $http.get('/rest/message-reports/all');
            },


            /** Creates a new report **/
            createReport: function(report) {
                return $http.post('/rest/message-reports/report/', report);
            },


            /** Updates the given report **/
            updateReport: function(report) {
                return $http.put('/rest/message-reports/report/' + report.reportId, report);
            },


            /** Deletes the given report **/
            deleteReport: function(report) {
                return $http['delete']('/rest/message-reports/report/' + report.reportId);
            },

            /** Returns the ticket that can be used to generate an export file that requires the given role */
            reportsTicket: function (role) {
                var param = role ? '?role=' + role : '';
                return $http.get('/rest/tickets/ticket' + param);
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
    }])


    /**
     * ********************************************************************************
     * AdminMailsService
     * ********************************************************************************
     * Interface for calling scheduled mails-related functions at the application server
     */
    .factory('AdminMailsService', [ '$http', function($http) {
        'use strict';

        return {
            search: function(params, page) {
                var p = 'maxSize=' + page.maxSize + '&page=' + (page.page - 1);
                if (params.recipient && params.recipient.length > 0) {
                    p += '&recipient=' + encodeURIComponent(params.recipient);
                }
                if (params.sender && params.sender.length > 0) {
                    p += '&sender=' + encodeURIComponent(params.sender);
                }
                if (params.subject && params.subject.length > 0) {
                    p += '&subject=' + encodeURIComponent(params.subject);
                }
                if (params.status && params.status.length > 0) {
                    p += '&status=' + encodeURIComponent(params.status);
                }
                if (params.fromDate) {
                    p += '&from=' + params.fromDate.valueOf();
                }
                if (params.toDate) {
                    p += '&to=' + params.toDate.valueOf();
                }
                return $http.get('/rest/scheduled-mails/search?' + p);
            },

            getMailDetails: function (id) {
                return $http.get('/rest/scheduled-mails/scheduled-mail/' + id);
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
    }]);

