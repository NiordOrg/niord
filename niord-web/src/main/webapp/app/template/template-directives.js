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
 * Template directives.
 *
 * Templates are really just message categories of type "TEMPLATE", however, they are
 * used for generating messages in a standardized way.
 */
angular.module('niord.template')


    /****************************************************************
     * The template-field directive supports selecting a template via
     * the templateData.template attribute.
     * The directive can also be initialized with either a "main-type"
     * attribute, a list of AtoNs or a message template.
     ****************************************************************/
    .directive('templateField', [ 'TemplateService', 'MessageService', 'LangService',
            function (TemplateService, MessageService, LangService) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/template/template-field.html',
            replace: false,
            scope: {
                message:            "=",
                categoriesUpdated:  "&",
                messageUpdated:     "&",
                atons:              "=",
                mainType:           "@",
                type:               "@",
                class:              "@",
                placeholder:        "@",
                tabIndex:           "="
            },
            link: function(scope, element, attrs) {

                scope.formatParents = LangService.formatParents;
                scope.message = scope.message || {};
                scope.atons = scope.atons || [];
                scope.class = scope.class || "";
                scope.placeholder = scope.placeholder || "Select Templates";

                if (!scope.message.categories) {
                    scope.message.categories = [];
                }

                if (scope.tabIndex) {
                    var input = element.find("input");
                    input.attr('tabindex', scope.tabIndex);
                }


                /** Called whenever the categories have been updated, but not the message at large **/
                scope.flagCategoriesUpdated = function () {
                    if (attrs.categoriesUpdated) {
                        scope.categoriesUpdated();
                    }
                };


                /** Returns if a category has been defined **/
                scope.categoriesDefined = function () {
                    return scope.message.categories.length > 0;
                };


                // Use for template selection
                scope.searchResult = [];
                scope.refreshTemplates = function (name) {
                    if (scope.atons.length == 0 && (!name || name.length == 0)) {
                        return;
                    }
                    TemplateService.search(name, scope.type, null, scope.atons)
                        .success(function(response) {
                            scope.searchResult = response;
                        });
                };


                /** Removes the current category selection */
                scope.clearCategories = function () {
                    scope.message.categories.length = 0;
                    scope.flagCategoriesUpdated();
                };


                /** Opens the template selector/execution dialog **/
                function openTemplateDialog(operation, message) {
                    TemplateService.templateDialog(
                        operation,
                        scope.type,
                        message,
                        scope.atons
                    ).result.then(function (result) {

                        if (result.type == 'category') {
                            scope.message.categories.length = 0;
                            angular.forEach(result.message.categories, function (category) {
                                scope.message.categories.push(category);
                            });
                            scope.flagCategoriesUpdated();
                        } else if (result.type == 'message') {
                            scope.message = result.message;
                            if (scope.messageUpdated) {
                                scope.messageUpdated({ message: scope.message });
                            }
                        }
                    });
                }


                /** If AtoNs are defined, add the AtoNs as a geometry for the message **/
                function updateGeometry(message) {
                    if (scope.atons && scope.atons.length > 0) {
                        var featureCollection = {
                            type: 'FeatureCollection',
                            features: []
                        };
                        angular.forEach(scope.atons, function (aton) {
                            var feature = {
                                type: 'Feature',
                                properties: { aton: aton },
                                geometry: { type: 'Point',  coordinates: [ aton.lon, aton.lat ] }
                            };
                            featureCollection.features.push(feature);
                        });
                        if (!message.parts || message.parts.length == 0) {
                            message.parts = [{ type: 'DETAILS', eventDates: [], descs: [] }];
                        }
                        message.parts[0].geometry = featureCollection;
                    }
                    return message;
                }


                /** Opens the template selector/execution dialog **/
                scope.openTemplateDialog = function (operation) {
                    if (scope.message.mainType === undefined) {
                        var mainType = scope.mainType || 'NW';
                        MessageService.newMessageTemplate(mainType)
                            .success(function (message) {
                                message.categories = scope.message.categories;
                                updateGeometry(message);
                                openTemplateDialog(operation, message);
                            })
                    } else {
                        openTemplateDialog(operation, scope.message);
                    }
                }
            }
        }
    }]);





