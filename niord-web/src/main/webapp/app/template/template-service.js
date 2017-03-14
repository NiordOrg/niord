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
 * Template services.
 *
 * Templates are really just message categories of type "TEMPLATE", however, they are
 * used for generating messages in a standardized way.
 */
angular.module('niord.template')


    /**
     * The template service is used for querying executable category templates.
     */
    .service('TemplateService', ['$rootScope', '$http', '$uibModal',
        function ($rootScope, $http, $uibModal) {
            'use strict';


            /** Searches for message templates */
            this.search = function(name, type, parentCategory, atons) {
                atons = atons || [];
                var params =
                    '?domain=' + encodeURIComponent($rootScope.domain.domainId) +
                    '&lang=' + $rootScope.language;
                if (name) {
                    params += '&name=' + encodeURIComponent(name);
                }
                if (parentCategory) {
                    params += '&ancestorId=' + parentCategory.id;
                }
                if (type) {
                    params += '&type=' + type;
                }
                return $http.put('/rest/categories/search' + params, atons);
            };


            /** Refreshes the categories against the backend **/
            this.refreshCategories = function (categories) {
                var catIds = categories.map(function (cat) { return cat.id }).join(',');
                return $http.get('/rest/categories/search/' + catIds + '?lang=' + $rootScope.language)
            };


            /** Executes all category templates of the message **/
            this.executeCategoryTemplates = function(message) {
                return $http.put('/rest/categories/execute', { message: message });
            };


            /** Opens the template selector dialog **/
            this.templateDialog = function (operation, type, message, atons) {
                return $uibModal.open({
                    controller: "TemplateDialogCtrl",
                    templateUrl: "/app/template/template-dialog.html",
                    size: 'xl',
                    resolve: {
                        operation: function () { return operation; },
                        type: function () { return type; },
                        message: function () { return message; },
                        atons: function () { return atons; }
                    }
                });
            };
        }]);
