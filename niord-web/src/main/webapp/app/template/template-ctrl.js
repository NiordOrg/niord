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
                $scope.executeTab   = operation === 'execute' && $scope.message.categories.length > 0;
                $scope.selectTab    = !$scope.executeTab;
            };
            $scope.selectOperation(operation);


            /** Returns if the currently selected operation is the parameter one **/
            $scope.operationSelected = function (operation) {
                return $scope.operation === operation;
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
                $scope.$broadcast('useSelectedCategories', {});
            };


            /** Called when the message has been updated by executing a template **/
            $scope.messageSelected = function () {
                $scope.$broadcast('messageSelected', {});
            };


            /** Called to refresh the preview message by applying the selected template **/
            $scope.refreshPreviewMessage = function () {
                $scope.$broadcast('refreshMessage', {});
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


            // Called when OK is clicked in the selection tab
            $scope.$on('useSelectedCategories', function () {
                $scope.$close({ type: 'category', message: $scope.message });
            });


            // Hook up a key listener that can be used to navigate the template list
            function keydownListener(evt) {
                var focused = $('.selected-template').is(':focus');
                if (!$scope.focusedCategory || !focused || evt.isDefaultPrevented()) {
                    return evt;
                }
                var cats = $scope.searchResult;
                var index = $.inArray($scope.focusedCategory, cats);
                if (index === -1) {
                    cats = $scope.message.categories;
                    index = $.inArray($scope.focusedCategory, cats);
                }
                if (index === -1) {
                    return evt;
                }
                if (evt.which === 38 /* up arrow */  && index > 0) {
                    $scope.focusCategory(cats[index - 1]);
                } else if (evt.which === 40 /* down arrow */ && index < cats.length - 1) {
                    $scope.focusCategory(cats[index + 1]);
                } else if ((evt.which === 13 /* return */ || evt.which === 32 /* space */) && $scope.focusedCategory !== undefined) {
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
                if ($scope.operation === "select") {
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
                }
            };
            $scope.$watch("operation", $scope.refreshCategories, true);
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
                    $.grep($scope.message.categories, function (c) { return c.id === cat.id; }).length > 0;
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
    .controller('TemplateExecutionDialogCtrl', ['$scope', '$rootScope', '$timeout', 'growl',
                'LangService', 'TemplateService', 'MessageService', 'MapService',
        function ($scope, $rootScope, $timeout, growl,
                  LangService, TemplateService, MessageService, MapService) {
            'use strict';

            $scope.previewLang          = LangService.language();
            $scope.showStdTemplateField = {};
            $scope.templateData         = [];

            $scope.validTypes = {
                NW: { LOCAL_WARNING: false, COASTAL_WARNING: false, SUBAREA_WARNING: false, NAVAREA_WARNING: false },
                NM: { TEMPORARY_NOTICE: false, PRELIMINARY_NOTICE: false, PERMANENT_NOTICE: false, MISCELLANEOUS_NOTICE: false }
            };

            // Look up the list of parameter types
            $scope.paramTypes = [];
            $scope.paramTypeMap = {};
            TemplateService.templateParameterTypes()
                .success(function (paramTypes) {
                    $scope.paramTypes = TemplateService.sortParameterTypes(paramTypes);
                    angular.forEach($scope.paramTypes, function (paramType) {
                        $scope.paramTypeMap[paramType.name] = paramType;
                    })
                });


            // Called when OK is clicked in the execute tab
            $scope.$on('messageSelected', function () {
                $scope.executeTemplates(true);
            });


            // Called when Refresh is clicked in the execute tab
            $scope.$on('refreshMessage', function () {
                $scope.executeTemplates();
            });


            // When entering execution mode, update the message to match the selected templates
            $scope.enterExecutionMode = function () {

                if ($scope.operation !== 'execute') {
                    return;
                }

                // Compute the template categories
                $scope.templates = $.grep($scope.message.categories, function (cat) {
                    return cat.type === 'TEMPLATE' && cat.scriptResourcePaths !== undefined
                });

                // Ensure the presence of a message part for each template
                $scope.templateData.length = 0;
                for (var x = 0; x < $scope.templates.length; x++) {
                    if ($scope.message.parts.length <= x) {
                        $scope.message.parts.push({
                            type: 'DETAILS'
                        })
                    }
                    $scope.templateData.push({ positions: [] });
                }


                // Ensure that all message parts have a well-defined geometry
                angular.forEach($scope.message.parts, function (part) {
                    if (!part.geometry) {
                        part.geometry = { type: 'FeatureCollection', features: [] }
                    }
                    if (!part.eventDates) {
                        part.eventDates = [];
                    }
                });


                // Compute the editor fields to display
                angular.forEach($rootScope.stdTemplateFields, function (field) {
                    var show = false;
                    angular.forEach($scope.templates, function (template) {
                        show = show || $.inArray(field, template.stdTemplateFields) !== -1;
                    });
                    $scope.showStdTemplateField[field] = show;
                });


                // Determine the message series for the current domain and message mainType
                $scope.messageSeries = [];
                var msg = $scope.message;
                if ($rootScope.domain && $rootScope.domain.messageSeries) {
                    angular.forEach($rootScope.domain.messageSeries, function (series) {
                        if (series.mainType === msg.mainType) {
                            $scope.messageSeries.push(series);
                            if (msg.messageSeries && msg.messageSeries.seriesId === series.seriesId) {
                                msg.messageSeries = series;
                            }
                            // Compute the valid types for the message
                            Object.keys($scope.validTypes[msg.mainType]).forEach(function (key) {
                                $scope.validTypes[msg.mainType][key] |= series.types === undefined
                                    || series.types.length === 0 || ($.inArray(key, series.types) !== -1);
                            });
                        }
                    });
                }
                if ($scope.messageSeries.length === 1 && !$scope.message.messageSeries) {
                    $scope.message.messageSeries = $scope.messageSeries[0];
                }

                // Update template position data
                $scope.updateTemplatePositionData();
            };


            /** Called when the message type is updated. See if we can deduce the message series from the type **/
            $scope.typeSelected = function () {
                var type = $scope.message.type;

                // Find matching message series
                var series = $.grep($scope.messageSeries, function (s) {
                    return s.types === undefined || s.types.length === 0 || ($.inArray(type, s.types) !== -1);
                });
                if (series.length === 1) {
                    $scope.message.messageSeries = series[0];
                }

                // Adjust promulgations
                MessageService.adjustEditableMessage($scope.message)
                    .success(function (message) {
                        $scope.message.promulgations = message.promulgations;
                    })

            };


            /** Executes the message template **/
            $scope.executeTemplates = function (selectMessage) {
                if ($scope.operation === 'execute') {
                    if ($scope.message.categories && $scope.message.categories.length > 0) {
                        TemplateService
                            .executeCategoryTemplates($scope.message, $scope.templateData)
                            .success(function (message) {
                                LangService.sortMessageDescs(message);
                                $scope.message = message;
                                if (selectMessage) {
                                    $scope.$close({ type: 'message', message: $scope.message });
                                } else {
                                    $scope.initMessage(message);
                                }
                            })
                            .error(function (data, status) {
                                growl.error("Error executing template (code: " + status + ")", {ttl: 5000})
                            });
                    }
                }
            };


            /** Computes the areas intersecting with the current message geometry **/
            $scope.computeAreas = function () {
                // Create a combined geometry for all message parts
                var geometry = {
                    type: 'FeatureCollection',
                    features: MessageService.getMessageFeatures($scope.message)
                };
                if (geometry.features.length > 0) {
                    MessageService.intersectingAreas(geometry)
                        .success(function (areas) {
                            if (areas && areas.length > 0) {
                                 $scope.message.areas = areas;
                            }
                        });
                }
            };


            /**
             * Serializes the feature to a structure resembling the one produced by the GeoJsonAdapter.
             * The GeoJsonAdapter serializes MessagePart.geometry for use in the Freemarker templates,
             * and by adopting the same format, we can reuse Freemarker code for e.g. rendering position lists.
             **/
            function serializeGeometry(feature) {
                var coords = [];
                MapService.serializeReadableCoordinates(feature, coords);
                return [{
                    coordinates: coords.map( function (coord) {
                        return { coordinates: [ coord.lon, coord.lat ] }
                    })
                }];
            }


            /**
             * Updates the template position data. The template data associated with a
             * specific template (and message part), will be updated to contain a "positions" array
             * with an entry for each GeoJSON Feature of the message part geometry.
             *
             * The "positions" array in turn is used to create lists of positional template parameters.
             **/
            $scope.updateTemplatePositionData = function () {
                for (var x = 0; x < $scope.templates.length; x++) {
                    var featureCollection = $scope.message.parts[x].geometry;
                    var data = $scope.templateData[x];

                    // Compute the features proper
                    var features = $.grep(featureCollection.features, function (feature) {
                        return feature.properties.parentFeatureIds === undefined &&
                            feature.properties.restriction !== 'affected';
                    });

                    // Sync the "pos" array with the GeoJSON features of the message part
                    data.positions.length = features.length;

                    for (var f = 0; f < features.length; f++) {
                        var feature = features[f];
                        data.positions[f] = data.positions[f] || {};
                        data.positions[f].geometry = serializeGeometry(feature.geometry);
                        data.positions[f].aton = feature.properties.aton;
                    }
                }

                // Whenever the geometry of a message changes,
                // re-compute message areas from the geometry
                $scope.computeAreas();
            };


            /** Adds a new date interval to the list of message date intervals */
            $scope.addPartEventDate = function (part, focus) {
                part.eventDates.push({ allDay: true, fromDate: undefined, toDate: undefined });
                if (focus) {
                    $timeout(function () { $('.evt-date:last').find('input:first').focus(); }, 100)
                }
            };


            /** Adds a new date interval to the list of message date intervals if the list is empty */
            $scope.checkAddPartEventDate = function (part) {
                if (part.eventDates.length === 0) {
                    $scope.addPartEventDate(part, false);
                }
            };


            /** Deletes the given date interval from the list of message date intervals **/
            $scope.deletePartEventDate = function (part, dateInterval) {
                if ($.inArray(dateInterval, part.eventDates) > -1) {
                    part.eventDates.splice( $.inArray(dateInterval, part.eventDates), 1 );
                }
            };


            /** Adds a copy of the given date interval to the list with the given date offset */
            $scope.copyPartEventDate = function (part, dateInterval, offset) {
                var di = angular.copy(dateInterval);
                if (offset && offset > 0) {
                    if (di.fromDate) {
                        di.fromDate = moment(di.fromDate).add(1, "days").valueOf();
                    }
                    if (di.toDate) {
                        di.toDate = moment(di.toDate).add(1, "days").valueOf();
                    }
                }
                part.eventDates.push(di);
            };


            /** Adds a marking to the list of markings */
            $scope.addMarking = function (markings) {
                markings.push({ markingType: undefined, lightCharacter: undefined, distance: undefined, bearing: undefined });
            };


            /** Deletes the given marking from the list of markings **/
            $scope.deleteMarking = function (markings, marking) {
                if ($.inArray(marking, markings) > -1) {
                    markings.splice( $.inArray(marking, markings), 1 );
                }
            };


            /** Set the preview language **/
            $scope.previewLanguage = function (lang) {
                $scope.previewLang = lang;
                $scope.updatePreviewMessage();
            };


            /** Create a preview message, i.e. a message sorted to the currently selected language **/
            $scope.updatePreviewMessage = function () {
                $scope.previewMessage = undefined;
                if ($scope.message) {
                    $scope.previewMessage = angular.copy($scope.message);
                    LangService.sortMessageDescs($scope.previewMessage, $scope.previewLang);
                }
            };

            $scope.$watch("message", $scope.updatePreviewMessage, true);
            $scope.$watch("operation", function() {
                $scope.enterExecutionMode();
                $scope.updatePreviewMessage();
            }, true);

        }]);

