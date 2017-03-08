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
 * The common controllers
 */
angular.module('niord.common')

    /**
     * Language Controller
     */
    .controller('LangCtrl', ['$scope', '$window', 'LangService',
        function ($scope, $window, LangService) {
            'use strict';

            $scope.changeLanguage = function (lang) {
                LangService.changeLanguage(lang);
                $window.location.reload();
            }

        }])

    /**
     * Domain Controller
     */
    .controller('DomainCtrl', ['$scope', '$window', '$location', '$timeout', 'DomainService',
        function ($scope, $window, $location, $timeout, DomainService) {
            'use strict';

            $scope.changeDomain = function (domain) {
                // Reset all parameters
                $location.url($location.path());

                $timeout(function () {
                    // Change domain
                    DomainService.changeDomain(domain);
                    $window.location.reload();
                });
            }

        }])


    /**
     * Controller handling cookies and disclaimer dialogs
     */
    .controller('FooterCtrl', ['$scope', '$uibModal',
        function ($scope, $uibModal) {
            'use strict';

            $scope.cookiesDlg = function () {
                $uibModal.open({
                    templateUrl: '/app/common/cookies.html',
                    size: 'lg'
                });
            };

            $scope.disclaimerDlg = function () {
                $uibModal.open({
                    templateUrl: '/app/common/disclaimer.html',
                    size: 'lg'
                });
            }
        }])


    /**
     * Controller for the category selection dialog
     */
    .controller('CategoryDialogCtrl', ['$scope', '$timeout', 'AdminCategoryService',
        function ($scope, $timeout, AdminCategoryService) {
            'use strict';

            $scope.categories = [];
            $scope.categoryFilter = '';


            // Set focus to the filter input field
            $timeout(function () {
                $('#filter').focus()
            }, 100);


            /** Load the categories */
            $scope.loadCategories = function() {
                AdminCategoryService
                    .getCategories()
                    .success(function (categories) {
                        $scope.categories = categories;
                        $timeout(function() {
                            $scope.$broadcast('entity-tree', 'expand-all');
                        });
                    });
            };


            /** Called when an categories is selected */
            $scope.selectCategory = function (category) {
                $scope.$apply(function() {
                    $scope.$close(category);
                });
            };
        }])

    /**
     * Controller for the area selection dialog
     */
    .controller('AreaDialogCtrl', ['$scope', '$timeout', 'AdminAreaService',
        function ($scope, $timeout, AdminAreaService) {
            'use strict';

            $scope.areas = [];
            $scope.areaFilter = '';


            // Set focus to the filter input field
            $timeout(function () {
                $('#filter').focus()
            }, 100);


            /** Load the areas */
            $scope.loadAreas = function() {
                AdminAreaService
                    .getAreas()
                    .success(function (areas) {
                        $scope.areas = areas;
                    });
            };


            /** Called when an areas is selected */
            $scope.selectArea = function (area) {
                $scope.$apply(function() {
                    $scope.$close(area);
                });
            };
        }])


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



