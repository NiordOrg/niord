
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
     * AdminFmTemplateService
     * ********************************************************************************
     * Interface for calling Freemarker template-related functions at the application server
     */
    .factory('AdminFmTemplateService', [ '$http', function($http) {
        'use strict';

        return {

            /** Returns all Freemarker templates **/
            getFmTemplates: function () {
                return $http.get('/rest/fm-templates/all');
            },


            /** Creates a new Freemarker template **/
            createFmTemplate: function(template) {
                return $http.post('/rest/fm-templates/fm-template/', template);
            },


            /** Updates the given Freemarker template **/
            updateFmTemplate: function(template) {
                return $http.put('/rest/fm-templates/fm-template/' + template.id, template);
            },


            /** Deletes the given Freemarker template **/
            deleteFmTemplate: function(template) {
                return $http['delete']('/rest/fm-templates/fm-template/' + template.id);
            },


            /** Reload Freemarker templates from the file system **/
            reloadFmTemplates: function() {
                return $http.post('/rest/fm-templates/reload/');
            },

            /** Loads the Freemarker template history for the given Freemarker template **/
            getFmTemplateHistory: function(template) {
                return $http.get('/rest/fm-templates/fm-template/' + template.id + '/history');
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
     * AdminTemplateService
     * ********************************************************************************
     * Interface for calling message template-related functions at the application server
     */
    .factory('AdminTemplateService', [ '$http', '$rootScope', function($http, $rootScope) {
        'use strict';

        return {

            /** Returns all message templates **/
            getTemplates: function () {
                return $http.get('/rest/templates/all');
            },


            /** Searches all message templates **/
            searchTemplates: function (params, page) {
                var p = 'language=' + $rootScope.language + '&inactive=true'
                        + '&maxSize=' + page.maxSize + '&page=' + (page.page - 1);
                if (params.name && params.name.length > 0) {
                    p += '&name=' + params.name;
                }
                if (params.domain) {
                    p += '&domainId=' + params.domain.domainId;
                }
                if (params.category) {
                    p += '&categoryId=' + params.category.id;
                }
                return $http.get('/rest/templates/search?' + p);
            },


            /** Returns the template with the given ID **/
            getTemplate: function (id) {
                return $http.get('/rest/templates/template/' + id);
            },


            /** Creates a new message template **/
            createTemplate: function(template) {
                return $http.post('/rest/templates/template/', template);
            },


            /** Updates the given message template **/
            updateTemplate: function(template) {
                return $http.put('/rest/templates/template/' + template.id, template);
            },


            /** Deletes the given message template **/
            deleteTemplate: function(template) {
                return $http['delete']('/rest/templates/template/' + template.id);
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

