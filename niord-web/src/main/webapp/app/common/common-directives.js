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
 * Common directives.
 */
angular.module('niord.common')

    /********************************
     * Defines a domain field used for
     * selecting a domain
     ********************************/
    .directive('domainField', [ '$rootScope', function ($rootScope) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/common/domain-field.html',
            replace: false,
            scope: {
                domainData:     "=",
                domainChanged:  "&",
                multiple:       "="
            },
            link: function(scope, element, attrs) {
                scope.domainData = scope.domainData || {};
                scope.multiple = scope.multiple || false;

                if (scope.multiple && !scope.domainData.domains) {
                    scope.domainData.domains = [];
                }

                /** Called whenever the domain has been updated **/
                scope.domainUpdated = function () {
                    if (attrs.domainChanged) {
                        scope.domainChanged();
                    }
                };

                /** Refreshes the domain selection **/
                scope.refreshDomains = function (text) {
                    text = text || '';
                    scope.domains = $.grep($rootScope.domains, function (domain) {
                        return domain.name.toLowerCase().indexOf(text.toLowerCase()) !== -1;
                    });
                };
                scope.refreshDomains();


                /** Removes the current domain selection */
                scope.removeDomains = function () {
                    if (scope.multiple) {
                        scope.domainData.domains.length = 0;
                    } else {
                        scope.domainData.domain = undefined;
                    }
                    scope.domainUpdated();
                }
            }
        }
    }])


    /****************************************************************
     * The areas-field directive supports selecting either a
     * single area or a list of areas. For single-area selection use
     * areaData.area and for multi-area selection use areaData.areas.
     * Use "init-ids" to initialize the areas using a list of area ids.
     ****************************************************************/
    .directive('areasField', ['$rootScope', '$timeout', '$http', '$uibModal', 'LangService',
            function($rootScope, $timeout, $http, $uibModal, LangService) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/common/areas-field.html',
            scope: {
                areaData:       "=",
                areaChanged:    "&",
                initIds:        "=",
                domain:         "=",
                messageSorting: "=",
                geometry:       "=",
                multiple:       "=",
                tabIndex:       "="
            },
            link: function(scope, element, attrs) {

                scope.formatParents = LangService.formatParents;
                scope.areaData = scope.areaData || {};

                // Area search parameters
                scope.multiple = scope.multiple || false;
                scope.messageSorting = scope.messageSorting || false;
                scope.geometry = scope.geometry || false;

                if (scope.multiple && !scope.areaData.areas) {
                    scope.areaData.areas = [];
                }

                if (scope.tabIndex) {
                    $timeout(function () {
                        element.find("input").attr('tabindex', scope.tabIndex);
                    });
                }

                /** Called whenever the area has been updated **/
                scope.areaUpdated = function () {
                    if (attrs.areaChanged) {
                        scope.areaChanged();
                    }
                };


                // init-ids can be used to instantiate the field from a list of area IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/areas/search/' + initIds.join() + '?lang=' + $rootScope.language + '&limit=20')
                            .then(function(response) {
                                // Reset the initId array
                                initIds.length = 0;
                                // Update the loaded entities
                                angular.forEach(response.data, function (area) {
                                    if (scope.multiple) {
                                        scope.areaData.areas.push(area);
                                    } else {
                                        scope.areaData.area = area;
                                    }
                                });
                            });
                    }
                }, true);


                /** Refreshes the areas search result */
                scope.searchResult = [];
                scope.refreshAreas = function(name) {
                    if (!name || name.length === 0) {
                        return [];
                    }
                    var domainParam = scope.domain ? '&domain=' + encodeURIComponent(scope.domain.domainId) : '';
                    return $http.get(
                        '/rest/areas/search?name=' + encodeURIComponent(name) +
                        domainParam +
                        '&messageSorting=' + scope.messageSorting +
                        '&geometry=' + scope.geometry +
                        '&lang=' + $rootScope.language +
                        '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Removes the current area selection */
                scope.removeAreas = function () {
                    if (scope.multiple) {
                        scope.areaData.areas.length = 0;
                    } else {
                        scope.areaData.area = undefined;
                    }
                    scope.areaUpdated();
                };


                /** Opens the area selector dialog **/
                scope.openAreaDialog = function () {
                    $uibModal.open({
                        controller: "AreaDialogCtrl",
                        templateUrl: "/app/common/area-dialog.html",
                        size: 'md'
                    }).result.then(function (area) {
                        if (scope.multiple) {
                            scope.areaData.areas.push(area);
                        } else {
                            scope.areaData.area = area;
                        }
                        scope.areaUpdated();
                    });
                }
            }
        }
    }])


    /****************************************************************
     * The categories-field directive supports selecting either a
     * single category or a list of categories. For single-category
     * selection use categoryData.category and for multi-category
     * selection use categoryData.categories.
     * Use "init-ids" to initialize the categories using a list of
     * category ids.
     ****************************************************************/
    .directive('categoriesField', ['$rootScope', '$timeout', '$http', '$uibModal', 'LangService',
            function($rootScope, $timeout, $http, $uibModal, LangService) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/common/categories-field.html',
            scope: {
                categoryData:    "=",
                categoryChanged: "&",
                initIds:         "=",
                domain:          "=",
                multiple:        "=",
                placeholder:     "@",
                tabIndex:        "="
            },
            link: function(scope, element, attrs) {

                scope.formatParents = LangService.formatParents;
                scope.categoryData = scope.categoryData || {};
                scope.placeholder = scope.placeholder || "Select Category";

                // Category search parameters
                scope.multiple = scope.multiple || false;

                if (scope.multiple && !scope.categoryData.categories) {
                    scope.categoryData.categories = [];
                }

                if (scope.tabIndex) {
                    $timeout(function () {
                        element.find("input").attr('tabindex', scope.tabIndex);
                    });
                }

                /** Called whenever the category has been updated **/
                scope.categoryUpdated = function () {
                    if (attrs.categoryChanged) {
                        scope.categoryChanged();
                    }
                };


                // init-ids can be used to instantiate the field from a list of category IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/categories/search/' + initIds.join() + '?lang=' + $rootScope.language + '&limit=20')
                            .then(function(response) {
                                // Reset the initId array
                                initIds.length = 0;
                                // Update the loaded entities
                                angular.forEach(response.data, function (category) {
                                    if (scope.multiple) {
                                        scope.categoryData.categories.push(category);
                                    } else {
                                        scope.categoryData.category = category;
                                    }
                                });
                            });
                    }
                }, true);


                /** Refreshes the categories search result */
                scope.searchResult = [];
                scope.refreshCategories = function(name) {
                    if (!name || name.length === 0) {
                        return [];
                    }
                    var domainParam = scope.domain ? '&domain=' + encodeURIComponent(scope.domain.domainId) : '';
                    return $http.get(
                        '/rest/categories/search?name=' + encodeURIComponent(name) +
                        domainParam +
                        '&lang=' + $rootScope.language +
                        '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Removes the current category selection */
                scope.removeCategories = function () {
                    if (scope.multiple) {
                        scope.categoryData.categories.length = 0;
                    } else {
                        scope.categoryData.category = undefined;
                    }
                    scope.categoryUpdated();
                };


                /** Opens the category selector dialog **/
                scope.openCategoryDialog = function () {
                    $uibModal.open({
                        controller: "CategoryDialogCtrl",
                        templateUrl: "/app/common/category-dialog.html",
                        size: 'md'
                    }).result.then(function (category) {
                        if (scope.multiple) {
                            scope.categoryData.categories.push(category);
                        } else {
                            scope.categoryData.category = category;
                        }
                        scope.categoryUpdated();
                    });
                }
            }
        }
    }])


    /****************************************************************
     * The charts-field directive supports selecting either a
     * single chart or a list of charts. For single-chart selection use
     * chartData.chart and for multi-chart selection use chartData.charts.
     * Use "init-ids" to initialize the charts using a list of chart ids.
     ****************************************************************/
    .directive('chartsField', ['$rootScope', '$timeout', '$http', function($rootScope, $timeout, $http) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/common/charts-field.html',
            scope: {
                chartData:      "=",
                initIds:        "=",
                geometry:       "=",
                multiple:       "=",
                tabIndex:       "="
            },
            link: function(scope, element) {

                scope.chartData = scope.chartData || {};

                // Chart search parameters
                scope.multiple = scope.multiple || false;
                scope.geometry = scope.geometry || false;

                if (scope.multiple && !scope.chartData.charts) {
                    scope.chartData.charts = [];
                }

                if (scope.tabIndex) {
                    $timeout(function () {
                        element.find("input").attr('tabindex', scope.tabIndex);
                    });
                }

                // init-ids can be used to instantiate the field from a list of chart IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/charts/search/' + initIds.join() + '?lang=' + $rootScope.language + '&limit=20')
                            .then(function(response) {
                                // Reset the initId array
                                initIds.length = 0;
                                // Update the loaded entities
                                angular.forEach(response.data, function (chart) {
                                    if (scope.multiple) {
                                        scope.chartData.charts.push(chart);
                                    } else {
                                        scope.chartData.chart = chart;
                                    }
                                });
                            });
                    }
                }, true);


                /** Refreshes the chart search result */
                scope.searchResult = [];
                scope.refreshCharts = function(name) {
                    if (!name || name.length === 0) {
                        return [];
                    }
                    return $http.get(
                        '/rest/charts/search?name=' + encodeURIComponent(name) +
                        '&geometry=' + scope.geometry +
                        '&lang=' + $rootScope.language +
                        '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Removes the current chart selection */
                scope.removeCharts = function () {
                    if (scope.multiple) {
                        scope.chartData.charts.length = 0;
                    } else {
                        scope.chartData.chart = undefined;
                    }
                };
            }
        }
    }])


    /****************************************************************
     * The editor-fields-field directive supports selecting a list of
     * editor fields via the editorData.editorFields attribute.
     ****************************************************************/
    .directive('editorFieldsField', ['$rootScope', function($rootScope) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/common/editor-fields-field.html',
            scope: {
                editorData:     "=",
                includeAll:     "=",
                placeholder:    "@"
            },
            link: function(scope) {

                scope.placeholder = scope.placeholder || "Select editor fields";
                scope.editorData = scope.editorData || {};
                scope.editorData.editorFields = scope.editorData.editorFields || [];

                /** Refreshes the chart search result */
                scope.searchResult = [];
                scope.refreshEditorFields = function(name) {
                    if (!name || name.length === 0) {
                        return [];
                    }
                    scope.searchResult.length = 0;
                    angular.forEach($rootScope.editorFieldsBase, function (value, key) {
                        // Add it to the search result, if:
                        // 1) It is not included by default (or includeAll is set)
                        // 2) The typed name is a substring match
                        // 3) It is not already selected
                        if ((!value || scope.includeAll) &&
                            key.toUpperCase().indexOf(name.toUpperCase()) !== -1 &&
                            $.inArray(key, scope.editorData.editorFields) === -1) {
                            scope.searchResult.push(key);
                        }
                    });
                };


                /** Removes the current editor fields selection */
                scope.removeEditorFields = function () {
                    scope.editorData.editorFields.length = 0;
                };
            }
        }
    }])


    /********************************
     * Defines a user field used for
     * selecting a user
     ********************************/
    .directive('userField', [ '$rootScope', 'UserService', 'AuthService',
        function ($rootScope, UserService, AuthService) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/common/user-field.html',
            replace: false,
            scope: {
                userData: "="
            },
            link: function(scope) {

                scope.loggedIn = AuthService.loggedIn;
                scope.currentUser = AuthService.preferredUsername();

                /** Refreshes the user selection **/
                scope.users = [];
                scope.refreshUsers = function (text) {
                    text = text || '';
                    if (scope.loggedIn && text.length > 0) {
                        UserService.search(text)
                            .success(function (users) {
                                scope.users.length = 0;
                                angular.forEach(users, function (user) {
                                    scope.users.push(user);
                                })
                            });
                    }
                };

                /** Removes the current user selection */
                scope.removeUser = function () {
                    scope.userData.username = undefined;
                }
            }
        }
    }])



    /********************************
     * Defines an email field used for
     * selecting email addresses
     ********************************/
    .directive('emailAddressField', [ '$rootScope', 'UserService', 'AuthService',
        function ($rootScope, UserService, AuthService) {
            'use strict';

            return {
                restrict: 'E',
                templateUrl: '/app/common/email-address-field.html',
                replace: false,
                scope: {
                    emailData:  "=",
                    field:      "@",
                    title:      "@"
                },
                link: function(scope) {

                    scope.field = scope.field || "to"; // e.g. "to", "bc", "bcc", etc.
                    scope.emailData[scope.field] = scope.emailData[scope.field] || [];
                    scope.loggedIn = AuthService.loggedIn;

                    /** Refreshes the email address selection **/
                    scope.emailSelection = [];
                    scope.refreshEmails = function (text) {
                        text = text || '';
                        if (scope.loggedIn && text.length > 0) {
                            UserService.searchEmails(text)
                                .success(function (emails) {
                                    scope.emailSelection.length = 0;
                                    angular.forEach(emails, function (email) {
                                        scope.emailSelection.push(email);
                                    })
                                });
                        }
                    };

                    /** Removes the current email address selection */
                    scope.removeEmails = function () {
                        scope.emailData[scope.field].length = 0;
                        scope.emailSelection.length = 0;
                    }
                }
            }
        }])


    /********************************
     * Defines a publication field used for
     * selecting a publication
     ********************************/
    .directive('publicationField', [ '$rootScope', '$http','MessageService', 'LangService', 'AuthService',
        function ($rootScope, $http, MessageService, LangService, AuthService) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/common/publication-field.html',
            replace: false,
            scope: {
                publicationData:    "=",
                messagePublication: "=",
                mainType:           "@",
                type:               "@",
                status:             "@",
                initIds:            "=",
                multiple:           "=",
                tabIndex:           "=",
                publicationChanged: "&"
            },
            link: function(scope, element, attrs) {

                scope.loggedIn = AuthService.loggedIn;
                scope.publicationData = scope.publicationData || {};
                scope.multiple = scope.multiple || false;
                if (scope.multiple && !scope.publicationData.publications) {
                    scope.publicationData.publications = [];
                }


                if (scope.tabIndex) {
                    var input = element.find("input");
                    input.attr('tabindex', scope.tabIndex);
                }


                /** Called whenever the publication has been updated **/
                scope.publicationUpdated = function () {
                    if (attrs.publicationChanged) {
                        scope.publicationChanged();
                    }
                };

                // init-ids can be used to instantiate the field from a list of publications IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/publications/publication/' + initIds.join()).then(function(response) {
                            // Reset the initId array
                            initIds.length = 0;
                            // Update the loaded entities
                            angular.forEach(response.data, function (publication) {
                                LangService.sortDescs(publication);
                                if (scope.multiple) {
                                    scope.publicationData.publications.push(publication);
                                } else {
                                    scope.publicationData.publication = publication;
                                }
                            });
                        });
                    }
                }, true);


                /** Refreshes the publication selection **/
                scope.publications = [];
                scope.refreshPublications = function (text) {
                    text = text || '';
                    if (!scope.multiple || text.length > 0) {
                        MessageService.searchPublications(text, scope.messagePublication, scope.mainType, scope.type, scope.status, scope.loggedIn)
                            .success(function (publications) {
                                scope.publications = publications.data;
                            });
                    }
                };


                /** Removes the current publication selection */
                scope.removePublication = function () {
                    if (scope.multiple) {
                        scope.publicationData.publications.length = 0;
                    } else {
                        delete scope.publicationData['publication'];
                    }
                    scope.publicationUpdated();
                }
            }
        }
    }])


    /********************************
     * Defines a promulgation type search field used for
     * selecting one or more promulgation types
     ********************************/
    .directive('promulgationTypeField', [ '$rootScope', '$http',
        function ($rootScope, $http) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/common/promulgation-type-field.html',
            replace: false,
            scope: {
                promulgationTypeData:   "=",
                initIds:                "="
            },
            link: function(scope) {

                scope.promulgationTypeData = scope.promulgationTypeData || {};
                if (!scope.promulgationTypeData.promulgationTypes) {
                    scope.promulgationTypeData.promulgationTypes = [];
                }

                // init-ids can be used to instantiate the field from a list of promulgationType IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/promulgations/public-promulgation-type/' + initIds.join()).then(function(response) {
                            // Reset the initId array
                            initIds.length = 0;
                            // Update the loaded entities
                            scope.promulgationTypeData.promulgationTypes = response.data;
                        });
                    }
                }, true);


                /** Refreshes the promulgationType selection **/
                scope.promulgationTypes = [];
                scope.refreshPromulgationTypes = function (text) {
                    text = text || '';
                    if (text.length > 0) {
                        $http.get('/rest/promulgations/search-public-promulgation-type?type=' + encodeURIComponent(text))
                            .then(function(response) {
                                scope.promulgationTypes = response.data;
                            });
                    }
                };


                /** Removes the current promulgation type selection */
                scope.removePromulgationTypes = function () {
                    scope.promulgationTypeData.promulgationTypes.length = 0;
                }
            }
        }
    }])


    /********************************
     * Defines a NAVTEX editor field.
     * Shows a print margin after 40 chars
     ********************************/
    .directive('navtexField', [ function () {
        'use strict';

        return {
            restrict: 'E',
            template: '<div ui-ace="navtexEditorOptions" class="navtex" ng-model="navtexData"></div>',
            scope: {
                navtexData:    "="
            },

            compile: function() {
                // Note to self: The "compile" function is used rather than the "link" because the "ui-ace"
                // directive will not properly pick up the options, unless they are defined in the "pre" function below.
                return {
                    pre: function (scope) {

                        scope.navtexEditorOptions = {
                            useWrapMode : false,
                            showGutter: false,
                            onLoad: function (editor) {
                                editor.setOption('printMarginColumn', 40);
                                editor.$blockScrolling = 1;
                            }
                        }
                    },
                    post: function (scope, element, attrs) {}
                }
            }
        }
    }])


    /********************************
     * Used for inserting a help link.
     ********************************/
    .directive('help', [ '$rootScope', '$state', function ($rootScope, $state) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/common/help-link.html',
            scope: {
                type:       "@",    // either "manual" or "auto"
                manual:     "@",    // either "editor" (default value), "admin" or "sysadmin"
                section:    "@"     // sub-section of the manual
            },

            link: function(scope) {

                // There is an underlying assumption that the url to the help link has the format
                // http://docs.niord.org/${manual}-manual/manual.html#${section}

                scope.type = scope.type || 'manual';
                scope.helpLink = undefined;
                scope.manual = scope.manual || 'editor';


                /** Updates the help link **/
                scope.updateHelpLink = function () {
                    scope.helpLink = $rootScope['documentationUrl'];
                    if (!scope.helpLink.endsWith('/')) {
                        scope.helpLink += '/';
                    }
                    scope.helpLink += scope.manual + '-manual/manual.html';

                    if (scope.section !== undefined) {
                        scope.helpLink += '#' + encodeURIComponent(scope.section);
                    }
                };


                /** Based on the current router state, update an auto-type help link **/
                scope.updateAutoHelp = function (state) {
                    if (state && state.help && state.help['section'] !== undefined) {
                        scope.section = state.help['section'];
                        scope.manual = state.help['manual'] || 'editor';
                        scope.updateHelpLink();
                    } else {
                        scope.helpLink = undefined;
                    }
                };


                if ($rootScope['documentationUrl'] !== undefined) {

                    scope.updateHelpLink();

                    if (scope.type === 'auto') {

                        // Whenever the state changes, update the auto-type help link
                        $rootScope.$on('$stateChangeSuccess', function (event, toState) {
                            scope.updateAutoHelp(toState);
                        });

                        // Bootstrap the auto-type help link
                        scope.updateAutoHelp($state.current);
                    }
                }
            }
        }
    }])


    /** Use this directive to set focus **/
    .directive('focus', ['$timeout', function ($timeout) {
        'use strict';

        return function (scope, element, attrs) {
            scope.$watch(attrs.focus, function (newValue) {
                $timeout(function () {
                    newValue && element.focus();
                }, 100);
            }, true);
        };
    }])


    /**
     * Angular is crap for recursive directives
     * http://stackoverflow.com/questions/14430655/recursion-in-angular-directives
     */
    .directive("recursive", function($compile) {
        return {
            restrict: "EACM",
            priority: 100000,
            compile: function(tElement) {
                var contents = tElement.contents().remove();
                var compiledContents;
                return function(scope, iElement) {
                    if(!compiledContents) {
                        compiledContents = $compile(contents);
                    }
                    iElement.append(
                        compiledContents(scope,
                            function(clone) {
                                return clone; }));
                };
            }
        };
    })


    /**
     * Show element active/inactive depending on the current location.
     * Usage:
     * <pre>
     *     <li check-active="/search/*"><a href="search.html">Search</a></li>
     * </pre>
     * <p>
     * Inspired by:
     *   http://stackoverflow.com/questions/16199418/how-do-i-implement-the-bootstrap-navbar-active-class-with-angular-js
     * - but changed quite a bit.
     */
    .directive('checkActive', [ '$location', function ($location) {
        'use strict';

        return {
            restrict: 'A',
            scope: {
                checkActive: "@"
            },
            link: function (scope, element) {

                // Watch for the $location
                scope.$watch(function () {
                    return $location.path();
                }, function (newValue) {

                    var locMask = scope.checkActive.split("*").join(".*");
                    var regexp = new RegExp('^' + locMask + '$', ['i']);

                    if (regexp.test(newValue)) {
                        element.addClass('active');
                    } else {
                        element.removeClass('active');
                    }
                });
            }
        };

    }])


    /**
     * Emits a flag image
     */
    .directive('flag', [function () {
        return {
            restrict: 'E',
            template: "<img height='16'/>",
            replace: true,
            scope: {
                lang: "=",
                style: "@"
            },
            link: function(scope, element) {
                scope.$watch(function() {
                        return scope.lang;
                    },
                    function(newValue) {
                        if (newValue) {
                            element.attr('src', '/img/flags/' + newValue + '.png');
                        }
                    }, true);

                if (scope.style) {
                    element.attr('style', scope.style);
                }
            }
        };
    }])


    .directive('bgFlag', [function () {
        return {
            restrict: 'A',
            scope: {
                bgFlag: "="
            },
            link: function(scope, element) {
                if (scope.bgFlag) {
                    element.addClass("localized");
                    element.css({
                        background: "white url('/img/flags/" + scope.bgFlag + ".png') no-repeat 99% 0%",
                        backgroundSize: "auto 14px"
                    });
                }
            }
        };
    }])


    /**
     * Used for editing a lat-lon position in an input field using a mask.
     * <p>
     * TODO: Proper handling of positions outside the normal lat-lon ranges (e.g. 512°E)
     *       - Roll over to a valid position range.
     */
    .directive('positionInput', ['$timeout', function($timeout) {
        return {
            restrict: 'E',
            templateUrl:  '/app/common/position-input.html',
            replace: true,
            scope: {
                lat:            "=",
                lon:            "=",
                decimals:       "=",
                posClass:       "=",
                posRequired:    "=",
                posChange:      "&",
                placeholder:    "@"
            },
            compile: function() {

                // Note to self: The "compile" function is used rather than the "link" because the "uib-mask"
                // directive will not pick up "uib-options", unless they are defined in the "pre" function below.

                return {
                    pre: function (scope, element, attrs) {

                        scope.decimals = attrs.decimals ? scope.decimals : 3;
                        scope.placeholder = scope.placeholder || 'Latitude - Longitude';
                        scope.latlon = undefined;
                        scope.posClass = scope.posClass || {};

                        var decimalMask = '99999999'.substr(0, scope.decimals);
                        if (scope.decimals > 0) {
                            var decimalDelim = numeral(0.0).format('.0').substr(0, 1);
                            decimalMask = decimalDelim + decimalMask;
                        }

                        scope.latMask = '99° 59' + decimalMask + '\'Y';
                        scope.lonMask = '199° 59' + decimalMask + '\'X';
                        scope.mask = scope.latMask + "  -  " + scope.lonMask;
                        scope.options = {
                            maskDefinitions: { 'X': /[ewEW]/, 'Y': /[nsNS]/, '1': /[01]/, '5': /[0-5]/ }
                        };
                    },

                    post: function (scope, element, attrs) {

                        // Get a reference to the input field
                        var inputField = $(element[0]).find('input');

                        // When a blank input field loses focus, display placeholder
                        inputField.bind('blur', function () {
                            if (scope.latlon === undefined) {
                                inputField.attr('placeholder', scope.placeholder);
                            }
                        });

                        // Remove any place-holder from the input field when it is focused
                        inputField.bind('focus', function () {
                            inputField.removeAttr('placeholder');
                        });

                        // Initially, display the placeholder
                        $timeout(function() { inputField.attr('placeholder', scope.placeholder); });

                        /** Generic function that prepends a character to a string **/
                        function pad(n, width, z) {
                            z = z || '0';
                            n = n + '';
                            return n.length >= width ? n : new Array(width - n.length + 1).join(z) + n;
                        }


                        /**
                         * Parses the string value to a latitude or longitude
                         * <p>
                         * If the mask is e.g. "99° 59,999\'Y  -  199° 59,999\'X", then a valid position
                         * for the pos 56 30.444'N 11 05.111'E will have the format: "5630444N01105111E"
                         **/
                        function parse(val, lat) {
                            var degLen = lat ? 2 : 3;
                            if (val === undefined || val.length !== degLen + 2 + scope.decimals + 1) {
                                return undefined;
                            }
                            val = val.toUpperCase();
                            var degreeStr = val.substr(0, degLen);
                            var minuteStr = val.substr(degLen, 2 + scope.decimals);
                            var direction = val.substr(val.length - 1);
                            var sign = direction === 'N' || direction === 'E' ? 1 : -1;

                            return sign * (
                                    parseInt(degreeStr)
                                    + parseFloat(minuteStr.substr(0, 2) + '.' + minuteStr.substr(2)) / 60.0
                                );
                        }


                        /**
                         * Formats the decimal latitude or longitude as a string
                         * <p>
                         * If the mask is e.g. "99° 59,999\'Y  -  199° 59,999\'X", then a valid position
                         * for the pos 56 30.444'N 11 05.111'E will have the format: "5630444N01105111E"
                         */
                        function format(val, lat) {
                            if (val === undefined) {
                                return undefined;
                            }
                            var dir = lat ? (val < 0 ? 'S' : 'N') : (val < 0 ? 'W' : 'E');
                            val = Math.abs(val);
                            var degrees = Math.floor(val);
                            var minutes = (val - degrees) * 60;
                            var decimalStr = [ '0', '0.0', '0.00', '0.000', '0.0000', '0.00000'];

                            return pad(degrees, lat ? 2 : 3)
                                 + pad(Math.floor(minutes), 2)
                                 + numeral(minutes - Math.floor(minutes)).format(decimalStr[scope.decimals]).substr(2)
                                 + dir;
                        }


                        // Watch for changes to the underlying position model
                        scope.$watch("[lat,lon]", function () {
                            var latSpec = format(scope.lat, true);
                            var lonSpec = format(scope.lon, false);
                            scope.latlon = latSpec !== undefined && lonSpec !== undefined ? latSpec + lonSpec : undefined;
                        }, true);


                        // Watch for changes to the input field value
                        scope.$watch("latlon", function (latlon, oldLatlon) {
                            if (latlon === oldLatlon) {
                                return;
                            }
                            var latSpec = undefined;
                            var lonSpec = undefined;
                            if (latlon && latlon.length > 0) {
                                // NB: lon-spec is one char longer than lat-spec
                                var index = Math.floor(latlon.length / 2);
                                latSpec = latlon.substr(0, index);
                                lonSpec = latlon.substr(index);
                            }
                            scope.lat = parse(latSpec, true);
                            scope.lon = parse(lonSpec, false);


                            // Broadcast change
                            if (attrs.posChange) {
                                scope.posChange({});
                            }
                        }, true);


                        /** Called in order to clear the position input field **/
                        scope.clearPos = function () {
                            scope.latlon = undefined;
                            $timeout(function() { inputField[0].focus() } );
                        };
                    }
                }
            }
        }
    }])


    /**
     * Tag an input field with this directive to avoid changes making the form dirty. See:
     * http://stackoverflow.com/questions/28721959/angular-1-2-is-it-possible-to-exclude-an-input-on-form-dirty-checking/28722106
     */
    .directive('ignoreDirty', [function() {
        return {
            restrict: 'A',
            require: 'ngModel',
            scope: {
                ignoreDirty: "="
            },
            link: function(scope, elm, attrs, ctrl) {
                if (scope.ignoreDirty === true) {
                    ctrl.$setPristine = function() {};
                    ctrl.$pristine = false;
                }
            }
        }
    }])


    .directive('jsonData', [function () {
        return {
            restrict: 'E',
            scope: {
                data: "=",
                json: "="
            },
            link: function(scope) {
                try {
                    scope.data = JSON.parse(scope.json);
                } catch (e) {}
                scope.$watch(
                    function() { return scope.json; },
                    function () {
                        try {
                            scope.data = JSON.parse(scope.json);
                        } catch (e) {}
                    },
                    true);
                scope.$watch(
                    function() { return scope.data; },
                    function () {
                        try {
                            scope.json = JSON.stringify(scope.data);
                        } catch (e) {}
                    },
                    true);
            }
        };
    }])


    /**
     * Defines a view mode filter panel
     */
    .directive('viewModeFilterPanel', [function () {
        return {
            restrict: 'E',
            templateUrl: '/app/common/view-mode-filter-panel.html',
            replace: true,
            transclude: true,
            scope: {
                state: "=",
                filterName: "@",
                closable: '@',
                clearFilter: '&'
            },
            link: function(scope) {
                scope.closable = scope.closable || 'true';
                scope.close = function () {
                    if (scope.closable === 'true' && scope.clearFilter) {
                        scope.clearFilter({name: scope.filterName})
                    }
                }
            }
        };
    }])


    /**
     * Date-time picker based on:
     * http://eonasdan.github.io/bootstrap-datetimepicker/
     */
    .directive('dateTimePicker', ['$rootScope', function($rootScope) {
        return {
            require : '^ngModel',
            restrict : 'AE',
            replace : true,
            scope: {
                id:             '@',
                format:         '=',
                time:           '=',
                readonly:       '=',
                placeholder:    '@',
                size:           '@',
                icon:           '@',
                tabIndex:       '=',
                defaultTimeStartOfDay: '='
            },
            template : '<div class="input-group date" data-date-format="l">'
                     + '  <input type="text" class="input-{{size}} form-control" />'
                     + '  <span class="input-group-addon">'
                     + '    <span class="glyphicon" ng-class="icon"></span>'
                     + '  </span>'
                     + '</div>',

            link : function(scope, element, attrs, ctrl) {

                var locale = $rootScope.language;

                var input = element.find("input");

                scope.icon = scope.icon || 'glyphicon-calendar';
                scope.size = scope.size || 'sm';
                scope.format = scope.format || "DD/MM/YYYY HH:mm";
                scope.$watch("format", function () {
                    element.attr('data-date-format', scope.format);
                    if (picker) {
                        picker.format(scope.format);
                    }
                }, true);

                if (scope.readonly) {
                    input.attr('readonly', "readonly");
                }

                if (scope.id) {
                    $(element).attr('id', scope.id);
                }

                if (scope.placeholder) {
                    input.attr('placeholder', scope.placeholder);
                }

                if (scope.tabIndex) {
                    input.attr('tabindex', scope.tabIndex);
                }

                var picker = $(element).datetimepicker({
                    locale: locale,
                    showTodayButton: true,
                    showClear: true
                }).data('DateTimePicker');

                ctrl.$formatters.push(function (modelValue) {
                    var date;
                    if (!modelValue) {
                        date = null;
                        picker.date(null);
                    } else {
                        date = modelValue;
                        picker.date(moment(date));
                    }
                    return date
                });

                ctrl.$parsers.push(function () {
                    if (!picker.date()) {
                        return null;
                    }
                    return picker.date().valueOf();
                });

                /** If a time parameter has been specified, adjust the date accordingly **/
                function adjustTime() {
                    var date = picker.date();
                    var millis = null;
                    if (date) {
                        millis = date.valueOf();
                        if (scope.time && scope.time.length > 0) {
                            var hms = scope.time.split(":");
                            if (hms.length === 3) {
                                date.set({
                                    'hour': parseInt(hms[0]),
                                    'minute': parseInt(hms[1]),
                                    'second': parseInt(hms[2]),
                                    'millisecond': 0
                                });
                                if (millis !== date.valueOf()) {
                                    picker.date(date);
                                    millis = date.valueOf();
                                }
                            }
                        }
                    }
                    return millis;
                }

                if (!scope.time) {
                    var numEvents = 0;

                    if (scope.defaultTimeStartOfDay == true) {
                        element.bind('dp.change', function (e) {
                            if (!e.oldDate && e.date && numEvents > 0) {
                                $(element).data('DateTimePicker').date(moment().startOf('day').utc().toDate());
                            }
                            numEvents++;
                        });
                    } else if (scope.defaultTimeStartOfDay == false) {
                        element.bind('dp.change', function (e) {
                            if (!e.oldDate && e.date && numEvents > 0) {
                                $(element).data('DateTimePicker').date(moment().utc().toDate());
                            }
                            numEvents++;
                        });
                    } else if (scope.defaultTimeStartOfDay == undefined) {
                    }
                }

                scope.$watch("time", adjustTime, true);

                element.bind('dp.change dp.hide', function() {
                    var millis = adjustTime();

                    ctrl.$setViewValue(millis);
                    ctrl.$modelValue = millis;
                    ctrl.$render();

                    $rootScope.$$phase || $rootScope.$apply();
                });
            }
        };
    }])


    /**
     * File upload, based on:
     * https://github.com/nervgh/angular-file-upload
     * <p>
     * The directive takes the following attributes:
     * <ul>
     *   <li>url: The url to upload the file to. Mandatory.</li>
     *   <li>multiple: Support single or multiple file upload. Defaults to false.</li>
     *   <li>auto-upload: Automatically start upload. Defaults to false.</li>
     *   <li>remove-after-upload: Remove file from queue once uploaded. Defaults to false.</li>
     *   <li>success(result): Success callback function. Optional.</li>
     *   <li>error(status, statusText): Error callback function. Optional.</li>
     * </ul>
     */
    .directive('fileUpload', ['$rootScope', '$timeout', 'FileUploader', 'AuthService',
        function ($rootScope, $timeout, FileUploader, AuthService) {
        'use strict';

        return {
            restrict: 'AE',

            transclude: true,

            templateUrl: '/app/common/file-upload.html',

            scope: {
                url:                '=',
                multiple:           '=',
                dropText:           '@',
                uploadText:         '@',
                removeText:         '@',
                fileTypes:          '=',
                autoUpload:         '=',
                removeAfterUpload:  '=',
                data:               '=',
                success:            '&',
                error:              '&',
                tabIndex:           '='
            },

            compile: function(element, attrs) {

                if (attrs.dropText === undefined) {
                    attrs.$set("dropText", (attrs.multiple) ? 'or drop files here' : 'or drop file here');
                }

                // Return link function
                return {
                    pre: function (scope) {
                        // create a uploader with options
                        scope.uploader = new FileUploader({
                            scope: scope,
                            url: scope.url,
                            data: { uploadData: scope.data },
                            filters: []
                        });
                    },

                    post: function (scope, element) {

                        scope.extension = function (txt) {
                            return txt.substr((~-txt.lastIndexOf(".") >>> 0) + 2);
                        };

                        scope.uploadText = scope.uploadText || "Upload all";
                        scope.removeText = scope.removeText || "Remove all";

                        if (scope.tabIndex) {
                            $timeout(function () {
                                element.find("input").attr('tabindex', scope.tabIndex);
                            });
                        }

                        if (scope.data) {
                            scope.uploader.onBeforeUploadItem = function (item) {
                                item.formData.push({ data: JSON.stringify(scope.data) });
                            };
                        }

                        // Check if file-types are defined
                        if (scope.fileTypes) {
                            scope.uploader.filters.push({
                                name: 'filterName',
                                fn: function (item) {
                                    var ext = scope.extension(item.name).toLowerCase();
                                    return (ext && $.inArray(ext, scope.fileTypes.toLowerCase().split(",")) > -1);
                                }});
                        }

                        // Auto-upload
                        if (scope.autoUpload) {
                            scope.uploader.autoUpload = scope.autoUpload;
                        }

                        // Remove after upload
                        if (scope.removeAfterUpload) {
                            scope.uploader.removeAfterUpload = scope.removeAfterUpload;
                        }

                        // Handle domain and authentication headers (todo: move to common function)
                        if ($rootScope.domain) {
                            scope.uploader.headers.NiordDomain = $rootScope.domain.domainId;
                        }
                        if (AuthService.keycloak.token) {
                            scope.uploader.headers.Authorization = 'bearer ' + AuthService.keycloak.token;
                        }

                        scope.cancelOrRemove = function (item) {
                            if (item.isUploading) {
                                item.cancel();
                            } else {
                                item.remove();
                            }
                        };

                        scope.$watch(function () {
                            return scope.url;
                        }, function (value) {
                            scope.uploader.url = value;
                        }, true);

                        // Success call-back
                        if (scope.success) {
                            scope.uploader.onSuccessItem = function (item, response) {
                                scope.success({ result: response});
                            };
                        }

                        // Error call-back
                        if (scope.error) {
                            scope.uploader.onErrorItem = function (item, response, status) {
                                scope.error({ status: status, statusText: response.statusText });
                            };
                        }
                    }
                }
            }

        }
    }])


    /**
     * Directive that wraps the fancytree jQuery plugin
     * Used for hierarchical entities such as areas and categories
     */
    .directive('entityTree', [ function () {
        'use strict';

        return {
            restrict: 'AE',
            scope: {
                entities:           '=',
                filter:             '=',
                flagInactive:       '=',
                flagTemplate:       '=',
                sort :              '@',
                entitySelected :    '&',
                entityMoved :       '&'
            },

            link: function (scope, element, attrs) {

                scope.sort = (attrs.sort !== undefined) ? attrs.sort : false;

                // Initialize the tree
                if (attrs.entityMoved) {
                    // DnD-enabled tree
                    element.fancytree({
                        source: [],
                        keyboard: true,
                        extensions: ["filter", "dnd"],
                        filter: {
                            mode: "hide"
                        },
                        dnd: {
                            autoExpandMS: 400,
                            draggable: {
                                zIndex: 1000,
                                scroll: false
                            },
                            preventVoidMoves: true,
                            preventRecursiveMoves: true,
                            dragStart: function() { return true; },
                            dragEnter: function(node, data) {
                                if (node.parent === data.otherNode.parent) {
                                    return ['over'];
                                }
                                return true;
                            },
                            dragOver: function(node, data) {},
                            dragLeave: function(node, data) {},
                            dragStop: function(node, data) {},
                            dragDrop: function(node, data) {
                                handleDragDrop(node, data);
                            }
                        },
                        activate: function(event, data){
                            var node = data.node;
                            if (scope.entitySelected) {
                                scope.entitySelected({ entity: node.data.entity });
                            }
                        }
                    });

                } else {
                    // Non-DnD tree
                    element.fancytree({
                        source: [],
                        keyboard: true,
                        extensions: ["filter"],
                        filter: {
                            mode: "hide"
                        },
                        activate: function(event, data){
                            var node = data.node;
                            if (scope.entitySelected) {
                                scope.entitySelected({ entity: node.data.entity });
                            }
                        }
                    });
                }

                var tree = element.fancytree("getTree");


                /** Listens for a 'entity-tree' events **/
                scope.$on('entity-tree', function(event, type) {
                    switch (type) {
                        case 'expand-all':
                            scope.expandAll();
                            break;
                        case 'collapse-all':
                            scope.collapseAll();
                            break;
                    }
                });


                /** Computes the icon to display **/
                function computeIcon(entity, node) {
                    var showInactive = scope.flagInactive && !entity.active;
                    var showTemplate = scope.flagTemplate && entity.type === 'TEMPLATE';
                    if (showInactive && showTemplate) {
                        node.icon = '/img/tree-icons/inactive-folder-tmpl.gif';
                    } else if (showInactive) {
                        node.icon = '/img/tree-icons/inactive-folder.gif';
                    } else if (showTemplate) {
                        node.icon = '/img/tree-icons/folder-tmpl.gif';
                    }
                }


                /**
                 * Convert the list of entities into the tree structure used by
                 * https://github.com/mar10/fancytree/
                 */
                function toTreeData(entities, treeData, level) {
                    for (var i = 0; i < entities.length; i++) {
                        var entity = entities[i];
                        var title = (entity.descs && entity.descs.length > 0) ? entity.descs[0].name : 'N/A';
                        var node = { key: entity.id, title: title, folder: true, children: [], level: level, entity: entity };
                        computeIcon(entity, node);
                        treeData.push(node);
                        if (entity.children && entity.children.length > 0) {
                            toTreeData(entity.children, node.children, level + 1);
                        }
                    }
                }


                /** Called when a dragged element has been dropped */
                function handleDragDrop(node, data) {
                    if (scope.entityMoved) {
                        var entity = data.otherNode.data.entity;
                        var parent = undefined;
                        if (data.hitMode === 'before' || data.hitMode === 'after') {
                            parent = (node.parent.data.entity) ? node.parent.data.entity : undefined;
                        } else if (data.hitMode === 'over') {
                            parent = node.data.entity;
                        }
                        scope.entityMoved({ entity: entity, parent: parent });

                    } else {
                        data.otherNode.moveTo(node, data.hitMode);
                    }
                }

                /** Watch entities **/
                scope.$watchCollection(function () {
                    return scope.entities;
                }, function (newValue) {
                    if (tree.options.source && tree.options.source.length > 0) {
                        scope.storeState();
                    }
                    var treeData = [];
                    if (newValue) {
                        toTreeData(newValue, treeData, 0);
                    }
                    tree.options.source = treeData;
                    tree.reload();
                    if (scope.sort) {
                        tree.rootNode.sortChildren(null, true);
                    }
                    tree.clearFilter();
                    scope.collapseAll();
                    scope.restoreState();
                    if (scope.filter) {
                        tree.filterNodes(scope.filter);
                    }
                });


                /** Watch the filter **/
                if (attrs.filter) {
                    scope.$watch(function () {
                        return scope.filter
                    }, function (newValue) {
                        var val = newValue || '';
                        if (val !== '') {
                            tree.filterNodes(val);
                            scope.expandAll();
                        } else {
                            tree.clearFilter();
                            scope.collapseAll();
                        }
                    }, true);
                }


                /** Stores the current expanded state */
                scope.storeState = function() {
                    scope.expandedIds = [];
                    scope.activeKey = tree.getActiveNode() ? tree.getActiveNode().key : undefined;
                    tree.visit(function(node){
                        if (node.expanded) {
                            scope.expandedIds.push(node.key);
                        }
                    });
                };


                /** Restores the previously stored expanded state */
                scope.restoreState = function() {
                    if (scope.expandedIds) {
                        tree.visit(function(node){
                            node.setExpanded($.inArray(node.key, scope.expandedIds) > -1);
                        });
                    }
                    if (scope.activeKey) {
                        tree.activateKey(scope.activeKey);
                    }
                };


                /** Collapses all tree nodes except the root node */
                scope.collapseAll = function() {
                    tree.visit(function(node){
                        node.setExpanded(node.data.level === 0);
                    });

                };


                /** Expands all tree nodes */
                scope.expandAll = function() {
                    tree.visit(function(node){
                        node.setExpanded(true);
                    });
                };
            }
        };
    }]);







