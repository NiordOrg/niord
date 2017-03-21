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
 * Common directives.
 */
angular.module('niord.common')


    /*************************************
     * Script resource type badge
     *************************************/
    .directive('scriptResourceType', [ function () {
        'use strict';

        return {
            restrict: 'E',
            template: '<span class="label label-script-resource-type" ng-class="typeClass">{{typeName}}</span>',
            scope: {
                resource:  "="
            },
            link: function(scope) {

                /** Updates the script resource type badge **/
                function updateTypeBadge() {
                    scope.typeClass = 'script-resource-type-' + scope.resource.type;
                    scope.typeName = scope.resource.type.toLowerCase();
                }

                scope.$watch('resource', updateTypeBadge, true);
            }
        }
    }])


    /****************************************************************
     * The script-resources-field directive supports selecting either
     * a single script resource path (using pathData.templatePath) or
     * manage a list of script resource paths (using
     * pathData.scriptResourcePaths).
     ****************************************************************/
    .directive('scriptResourcesField', ['$timeout', 'AdminScriptResourceService',
        function($timeout, AdminScriptResourceService) {

            return {
                restrict: 'E',
                replace: true,
                templateUrl: '/app/sysadmin/script-resources-field.html',
                scope: {
                    pathData:       "=",
                    pathsChanged:   "&",
                    multiple:       "=",
                    pattern:        "@"
                },
                link: function(scope, element, attrs) {

                    scope.pathData = scope.pathData || {};
                    scope.pattern  = scope.pattern || ".+\.(ftl|FTL|js|JS)";

                    /**
                     * Annoyingly, ng-repeat does not work properly with a list of strings (scriptResourcePaths).
                     * See: https://github.com/angular/angular.js/issues/1267
                     * So, we use this method to wrap the "scriptResourcePaths" list into a "scriptResourcePaths"
                     * list with objects
                     */
                    scope.scriptResourcePaths = [];
                    if (scope.multiple) {

                        scope.$watch("pathData.id", function () {
                            scope.pathData.scriptResourcePaths = scope.pathData.scriptResourcePaths || [];
                            scope.scriptResourcePaths.length = 0;
                            if (scope.pathData.scriptResourcePaths) {
                                angular.forEach(scope.pathData.scriptResourcePaths, function (path) {
                                    scope.scriptResourcePaths.push({ 'path' : path })
                                })
                            }
                            if (scope.scriptResourcePaths.length == 0) {
                                scope.scriptResourcePaths.push({ path: '' });
                            }
                        }, true);

                        scope.$watch("scriptResourcePaths", function () {
                            scope.pathData.scriptResourcePaths.length = 0;
                            angular.forEach(scope.scriptResourcePaths, function (path) {
                                if (path.path && path.path != '') {
                                    scope.pathData.scriptResourcePaths.push(path.path)
                                }
                            })
                        }, true);
                    }


                    /** Called whenever the paths have been updated **/
                    scope.pathsUpdated = function () {
                        if (attrs.pathsChanged) {
                            scope.pathsChanged();
                        }
                    };


                    /** (multiple version) Script resource path DnD configuration **/
                    scope.pathsSortableCfg = {
                        group: 'scriptResourcePaths',
                        handle: '.move-btn',
                        onEnd: scope.pathsUpdated
                    };


                    /** (multiple version) Adds a new resource path after the given index **/
                    scope.addResourcePath = function (index) {
                        scope.scriptResourcePaths.splice(index + 1, 0, { 'path' : '' });
                        scope.pathsUpdated();
                        $timeout(function () {
                            angular.element($("#path_" + (index + 1))).focus();
                        });
                    };


                    /** (multiple version) Removes the resource path at the given index **/
                    scope.deleteResourcePath = function (index) {
                        scope.scriptResourcePaths.splice(index, 1);
                        scope.pathsUpdated();
                        if (scope.scriptResourcePaths.length == 0) {
                            scope.scriptResourcePaths.push({ 'path' : '' });
                        }
                    };


                    /** (multiple version) Opens a dialog for script resource selection **/
                    scope.selectResourcePath = function (index) {
                        AdminScriptResourceService.scriptResourceDialog()
                            .result.then(function (scriptResource) {
                            scope.scriptResourcePaths[index].path = scriptResource.path;
                            scope.pathsUpdated();
                        })
                    };


                    /** (single version) Opens a dialog for template selection **/
                    scope.selectTemplatePath = function () {
                        AdminScriptResourceService.scriptResourceDialog('FM')
                            .result.then(function (scriptResource) {
                            scope.pathData.templatePath = scriptResource.path;
                            scope.pathsUpdated();
                        })
                    };

                }
            }
        }])



   /****************************************************************
     * The template-params-field directive manages a list of
     * template parameters.
     ****************************************************************/
    .directive('templateParamsField', ['$uibModal', 'LangService',
        function($uibModal, LangService) {

            return {
                restrict: 'E',
                replace: true,
                templateUrl: '/app/sysadmin/template-params-field.html',
                scope: {
                    paramData:      "=",
                    paramsChanged:  "&"
                },
                link: function(scope, element, attrs) {

                    scope.paramData = scope.paramData || {};
                    scope.paramData.templateParams = scope.paramData.templateParams || [];

                    /** Called whenever the params have been updated **/
                    scope.paramsUpdated = function () {
                        if (attrs.paramsChanged) {
                            scope.paramsChanged();
                        }
                    };

                    // Used to ensure that description entities have a "name" field
                    function ensureNameField(desc) {
                        desc.name = '';
                    }


                    /** Template params DnD configuration **/
                    scope.paramsSortableCfg = {
                        group: 'templateParams',
                        handle: '.move-btn',
                        onEnd: scope.paramsUpdated
                    };


                    /** Open a dialog for editing the given template parameter **/
                    scope.editTemplateParamDialog = function (param) {
                        return $uibModal.open({
                            controller: function ($scope, AdminCategoryService, param) {
                                $scope.param = angular.copy(param);
                                $scope.paramTypes = [];

                                AdminCategoryService.templateParameterTypes()
                                    .success(function (paramTypes) {
                                        $scope.paramTypes = paramTypes;
                                    })
                            },
                            templateUrl: "/app/sysadmin/template-param-dialog.html",
                            size: 'md',
                            resolve: {
                                param: function () { return param; }
                            }
                        });
                    };


                    /** Adds a new template parameter to the executable category */
                    scope.addTemplateParam = function () {
                        var param =  LangService.checkDescs({
                            paramId: undefined,
                            type: 'text',
                            mandatory: false,
                            list: false,
                            descs: []
                        }, ensureNameField);
                        scope.editTemplateParamDialog(param)
                            .result.then(function (updatedParam) {
                            scope.paramData.templateParams.push(updatedParam);
                            scope.paramsUpdated();
                        });
                    };


                    /** Edits the given template parameter */
                    scope.editTemplateParam = function (param) {
                        scope.editTemplateParamDialog(param)
                            .result.then(function (updatedParam) {
                            angular.copy(updatedParam, param);
                            scope.paramsUpdated();
                        });
                    };


                    /** Removes the given template parameter */
                    scope.deleteTemplateParam = function (index) {
                        scope.paramData.templateParams.splice(index, 1);
                        scope.paramsUpdated();
                    };

                }
            }
        }]);

