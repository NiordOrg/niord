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
    .service('TemplateService', ['$rootScope', '$http', '$uibModal', 'LangService',
        function ($rootScope, $http, $uibModal, LangService) {
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


            /** Returns the template parameter types **/
            this.templateParameterTypes = function(lang) {
                var params = (lang !== undefined) ? '?lang=' + lang : '';
                return $http.get('/rest/templates/parameter-types' + params);
            };


            /** Sorts all parameter types incl. nested values according to the current language **/
            this.sortParameterTypes = function (paramTypes) {
                angular.forEach(paramTypes, function (paramType) {
                    LangService.sortDescs(paramType);
                    if (paramType.type === 'LIST' && paramType.values) {
                        angular.forEach(paramType.values, function (val) {
                            LangService.sortDescs(val);
                        })
                    } else if (paramType.type === 'COMPOSITE' && paramType.templateParams) {
                        angular.forEach(paramType.templateParams, function (param) {
                            LangService.sortDescs(param);
                        })
                    }
                });
                return paramTypes;
            };


            /** Returns the given promulgation type **/
            this.getPromulgationType = function (typeId) {
                return $http.get('/rest/promulgations/promulgation-type/' + encodeURIComponent(typeId));
            };


            /** Refreshes the categories against the backend **/
            this.refreshCategories = function (categories) {
                var catIds = categories.map(function (cat) { return cat.id }).join(',');
                return $http.get('/rest/categories/search/' + catIds + '?lang=' + $rootScope.language)
            };


            /** Executes all category templates of the message **/
            this.executeCategoryTemplates = function(message, params) {
                return $http.put('/rest/templates/execute', { message: message, templateParams: params });
            };


            /** Check if any of the dictionary values matches the given aton **/
            this.matchAtonToList = function (values, aton) {
                return $http.put('/rest/dictionaries/matches-aton', { values: values, aton : aton });
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
