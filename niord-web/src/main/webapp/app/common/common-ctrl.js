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
        }]);

