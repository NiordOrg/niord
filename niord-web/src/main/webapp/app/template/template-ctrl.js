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
     * Controller for template selection and execution dialog
     */
    .controller('TemplateDialogCtrl', ['$scope', '$document', '$timeout', 'LangService', 'TemplateService', 'MessageService',
                'operation', 'type', 'categories', 'message', 'atons',
        function ($scope, $document, $timeout, LangService, TemplateService, MessageService,
                  operation, type, categories, message, atons) {
            'use strict';

            $scope.formatParents  = LangService.formatParents;
            $scope.operation      = operation || 'select';
            $scope.type           = type;
            $scope.atons          = atons || [];
            $scope.template       = categories.length > 0 ? categories[0] : undefined;
            $scope.searchResult   = [];
            $scope.message        = angular.copy(message);
            $scope.params         = { name: '', category: undefined };
            $scope.exampleMessage = undefined;


            /** Initialize the tab-selection **/
            $scope.selectOperation = function (operation) {
                $scope.operation = operation;
                $scope.executeTab   = operation == 'execute' && $scope.template !== undefined;
                $scope.selectTab    = !$scope.executeTab;
            };
            $scope.selectOperation(operation);


            // Hook up a key listener that can be used to navigate the template list
            function keydownListener(evt) {
                var focused = $('.selected-template').is(':focus');
                if (!$scope.template || $scope.searchResult.length == 0 || !focused || evt.isDefaultPrevented()) {
                    return evt;
                }
                var index = $.inArray($scope.template, $scope.searchResult);
                if (evt.which == 38 /* up arrow */) {
                    if (index != -1 && index > 0) {
                        $scope.selectTemplate($scope.searchResult[index - 1]);
                        evt.preventDefault();
                        $scope.$$phase || $scope.$apply();
                    }
                } else if (evt.which == 40 /* down arrow */) {
                    if (index != -1 && index < $scope.searchResult.length - 1) {
                        $scope.selectTemplate($scope.searchResult[index + 1]);
                        evt.preventDefault();
                        $scope.$$phase || $scope.$apply();
                    }
                }
            }
            $document.on('keydown', keydownListener);
            $scope.$on('$destroy', function() {
                $document.off('keydown', keydownListener);
            });


            $timeout(function () {
                $('#templateFilter').focus();
            }, 100);

            /** Returns if the currently selected operation is the parameter one **/
            $scope.operationSelected = function (operation) {
                return $scope.operation == operation;
            };


            // Use for template selection
            $scope.refreshTemplates = function () {
                TemplateService.search($scope.params.name, $scope.type, $scope.params.category, $scope.atons)
                    .success(function(response) {
                        $scope.searchResult = response;
                    });
            };
            $scope.$watch("params", $scope.refreshTemplates, true);


            /** Clears the AtoN selection **/
            $scope.clearAtons = function () {
                $scope.atons.length = 0;
                $scope.refreshTemplates();
            };


            /** Selects the given template **/
            $scope.selectTemplate = function (template) {
                $scope.template = template;
                $scope.createExampleMessage();
            };


            /** Called when the message has been updated by executing a template **/
            $scope.messageSelected = function () {
                $scope.$close($scope.message);
            };


            /** Create an example message for the currently selected template **/
            $scope.createExampleMessage = function () {
                $scope.exampleMessage = undefined;
                if ($scope.template && $scope.template.messageId) {
                    MessageService.allLangDetails($scope.template.messageId)
                        .success(function (message) {
                            $scope.exampleMessage = LangService.sortDescs(message);
                        });
                }
            };

        }]);



