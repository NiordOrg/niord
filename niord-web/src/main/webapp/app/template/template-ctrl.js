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
 * The template controllers.
 *
 * Templates are really just message categories of type "TEMPLATE", however, they are
 * used for generating messages in a standardized way.
 */
angular.module('niord.template')


    /**
     * ******************************************************************
     * Controller for template selection and execution dialog.
     * Most of the actual functionality is handled by sub-controllers.
     * ******************************************************************
     */
    .controller('TemplateDialogCtrl', ['$scope', 'TemplateService',
                'operation', 'type', 'message', 'atons',
        function ($scope, TemplateService,
                  operation, type, message, atons) {
            'use strict';

            $scope.operation        = operation || 'select';
            $scope.type             = type;
            $scope.atons            = atons || [];
            $scope.message          = angular.copy(message);


            /** Initialize the tab-selection **/
            $scope.selectOperation = function (operation) {
                $scope.operation = operation;
                $scope.executeTab   = operation == 'execute' && $scope.message.categories.length > 0;
                $scope.selectTab    = !$scope.executeTab;
            };
            $scope.selectOperation(operation);


            /** Returns if the currently selected operation is the parameter one **/
            $scope.operationSelected = function (operation) {
                return $scope.operation == operation;
            };


            /** Initialize the message **/
            $scope.initMessage = function (message) {
                if (message !== undefined) {
                    // Allows sub-controller to override message of this parent controller
                    $scope.message = message;
                }

                // Refresh the categories associated with the message.
                // This is to ensure that we get e.g. the category.type flag along.
                if ($scope.message.categories.length > 0) {
                    TemplateService.refreshCategories($scope.message.categories)
                        .success(function (categories) {
                            $scope.message.categories = categories;
                        });
                }
            };
            $scope.initMessage();


            /** Returns if any category has been selected **/
            $scope.categoriesSelected = function () {
                return $scope.message.categories.length > 0;
            };


            /** Called when the user clicks the OK button **/
            $scope.useSelectedCategories = function () {
                $scope.$close({ type: 'category', message: $scope.message });
            };


            /** Called when the message has been updated by executing a template **/
            $scope.messageSelected = function () {
                $scope.$close({ type: 'message', message: $scope.message });
            };
        }])


    /**
     * ******************************************************************
     * TemplateDialogCtrl Sub-controller for template selection
     * ******************************************************************
     */
    .controller('TemplateSelectionDialogCtrl', ['$scope', '$document', '$timeout', 'LangService', 'TemplateService', 'MessageService',
        function ($scope, $document, $timeout, LangService, TemplateService, MessageService) {
            'use strict';

            $scope.formatParents    = LangService.formatParents;
            $scope.focusedCategory  = undefined;
            $scope.searchResult     = [];
            $scope.params           = { name: '', category: undefined };
            $scope.exampleMessage   = undefined;


            /** Script resource path DnD configuration **/
            $scope.categorySortableCfg = {
                group: 'selectedCategories',
                handle: '.move-btn'
            };


            // Hook up a key listener that can be used to navigate the template list
            function keydownListener(evt) {
                var focused = $('.selected-template').is(':focus');
                if (!$scope.focusedCategory || !focused || evt.isDefaultPrevented()) {
                    return evt;
                }
                var cats = $scope.searchResult;
                var index = $.inArray($scope.focusedCategory, cats);
                if (index == -1) {
                    cats = $scope.message.categories;
                    index = $.inArray($scope.focusedCategory, cats);
                }
                if (index == -1) {
                    return evt;
                }
                if (evt.which == 38 /* up arrow */  && index > 0) {
                    $scope.focusCategory(cats[index - 1]);
                } else if (evt.which == 40 /* down arrow */ && index < cats.length - 1) {
                    $scope.focusCategory(cats[index + 1]);
                } else if ((evt.which == 13 /* return */ || evt.which == 32 /* space */) && $scope.focusedCategory !== undefined) {
                    if ($scope.categorySelected($scope.focusedCategory)) {
                        $scope.unselectCategory($scope.focusedCategory);
                    } else {
                        $scope.selectCategory($scope.focusedCategory);
                    }
                }
                evt.preventDefault();
                $scope.$$phase || $scope.$apply();
            }
            $document.on('keydown', keydownListener);
            $scope.$on('$destroy', function() {
                $document.off('keydown', keydownListener);
            });


            $timeout(function () {
                $('#templateFilter').focus();
            }, 100);


            /** Updates the category search result **/
            $scope.refreshCategories = function () {

                $scope.focusedCategory = undefined;

                TemplateService.search($scope.params.name, $scope.type, $scope.params.category, $scope.atons)
                    .success(function(categories) {
                        // Remove categories already selected
                        var selCatIds = {};
                        angular.forEach($scope.message.categories, function (cat) {
                            selCatIds[cat.id] = true;
                        });
                        $scope.searchResult.length = 0;
                        angular.forEach(categories, function (cat) {
                            if (!selCatIds[cat.id]) {
                                $scope.searchResult.push(cat);
                            }
                        })
                    });
            };
            $scope.$watch("params", $scope.refreshCategories, true);
            $scope.$watchCollection("message.categories", $scope.refreshCategories);


            /** Clears the AtoN selection **/
            $scope.clearAtons = function () {
                $scope.atons.length = 0;
                $scope.refreshCategories();
            };


            /** Returns if any category has been selected **/
            $scope.categorySelected = function (cat) {
                return cat !== undefined &&
                    $.grep($scope.message.categories, function (c) { return c.id == cat.id; }).length > 0;
            };


            /** Selects the given category **/
            $scope.selectCategory = function (cat) {
                if (cat && !$scope.categorySelected(cat)) {
                    $scope.message.categories.push(cat);
                }
            };


            /** Unselects the given category **/
            $scope.unselectCategory = function (cat) {
                if (cat && $scope.categorySelected(cat)) {
                    var idx = $scope.message.categories.indexOf(cat);
                    if (idx !== -1) {
                        $scope.message.categories.splice(idx, 1);
                    }
                }
            };


            /** Removes all selected cagtegories **/
            $scope.clearCategorySelection = function () {
                $scope.message.categories.length = 0;
            };


            /** Returns if any category is currently focused **/
            $scope.categoryFocused = function () {
                return $scope.focusedCategory !== undefined;
            };


            /** Selects the given category **/
            $scope.focusCategory = function (category) {
                $scope.focusedCategory = category;
                $scope.createExampleMessage();
            };


            /** Create an example message for the currently selected template **/
            $scope.createExampleMessage = function () {
                $scope.exampleMessage = undefined;
                if ($scope.focusedCategory && $scope.focusedCategory.messageId) {
                    MessageService.allLangDetails($scope.focusedCategory.messageId)
                        .success(function (message) {
                            $scope.exampleMessage = LangService.sortDescs(message);
                        });
                }
            };

        }])


    /**
     * ******************************************************************
     * TemplateDialogCtrl Sub-controller for template execution
     * ******************************************************************
     */
    .controller('TemplateExecutionDialogCtrl', ['$scope', '$rootScope', 'growl', 'LangService', 'TemplateService',
        function ($scope, $rootScope, growl, LangService, TemplateService) {
            'use strict';

            $scope.previewLang      = LangService.language();
            $scope.editMode = {};
            $scope.showStdTemplateField = {};

            // Compute the template categories
            $scope.templates = $.grep($scope.message.categories, function (cat) {
                return cat.type == 'TEMPLATE' && cat.scriptResourcePaths !== undefined
            });

            // Compute the editor fields to display
            angular.forEach($rootScope.stdTemplateFields, function (field) {
                $scope.editMode[field] = [ false ];

                var show = false;
                angular.forEach($scope.templates, function (template) {
                    show = show || $.inArray(field, template.stdTemplateFields) != -1;
                });
               $scope.showStdTemplateField[field] = show;
            });


            // Determine the message series for the current domain and message mainType
            $scope.messageSeries = [];
            if ($rootScope.domain && $rootScope.domain.messageSeries) {
                angular.forEach($rootScope.domain.messageSeries, function (series) {
                    if (series.mainType == $scope.message.mainType) {
                        if ($scope.message.messageSeries && $scope.message.messageSeries.seriesId == series.seriesId) {
                            $scope.messageSeries.push($scope.message.messageSeries);
                        } else {
                            $scope.messageSeries.push(series);
                        }
                    }
                });
            }
            if ($scope.messageSeries.length == 1 && !$scope.message.messageSeries) {
                $scope.message.messageSeries = $scope.messageSeries[0];
            }


            /** Executes the message template **/
            $scope.executeTemplate = function () {

                var template = $scope.templates.length > 0 ? $scope.templates[0] : null;

                if (template) {
                    TemplateService
                        .executeCategoryTemplate(template, $scope.message)
                        .success(function (message) {
                            LangService.sortMessageDescs(message);
                            $scope.message = message;
                            $scope.initMessage(message);
                        })
                        .error(function (data, status) {
                            growl.error("Error executing template (code: " + status + ")", {ttl: 5000})
                        });
                }
            };


            /** Set the preview language **/
            $scope.previewLanguage = function (lang) {
                $scope.previewLang = lang;
                $scope.createPreviewMessage();
            };


            /** Create a preview message, i.e. a message sorted to the currently selected language **/
            $scope.createPreviewMessage = function () {
                $scope.previewMessage = undefined;
                if ($scope.operation == 'execute') {
                    if ($scope.message) {
                        $scope.previewMessage = angular.copy($scope.message);
                        LangService.sortMessageDescs($scope.previewMessage, $scope.previewLang);
                    }
                }
            };
            $scope.$watch("message", $scope.createPreviewMessage, true);
            $scope.$watch("operation", $scope.createPreviewMessage, true);

        }]);

