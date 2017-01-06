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
 * The main Niord message list app module
 */

angular.module('niord.auth', []);
angular.module('niord.admin', []);
angular.module('niord.atons', []);
angular.module('niord.messages', []);
angular.module('niord.common', []);
angular.module('niord.conf', []);
angular.module('niord.map', []);
angular.module('niord.editor', []);
angular.module('niord.schedule', []);
angular.module('niord.home', []);


var app = angular.module('niord.admin', [
        'ngSanitize', 'ui.bootstrap', 'ui.select', 'ui.router', 'ui.tinymce', 'ui.mask', 'pascalprecht.translate',
        'angular-growl', 'ng-sortable', 'angularFileUpload','jlareau.bowser',
        'niord.common', 'niord.auth', 'niord.admin', 'niord.atons', 'niord.messages',
        'niord.conf', 'niord.map', 'niord.editor', 'niord.schedule', 'niord.home' ])

    .config(['$stateProvider', '$urlRouterProvider', '$httpProvider', function ($stateProvider, $urlRouterProvider, $httpProvider) {
        'use strict';

        $httpProvider.interceptors.push('authHttpInterceptor');


        $urlRouterProvider
            .when('/messages', '/messages/table')
            .when('/atons', '/atons/grid')
            .when('/editor', '/editor/edit/')
            .when('/admin', '/admin/overview')
            .otherwise("/");

        $stateProvider

            /** Home **/
            .state('home', {
                url: "/",
                templateUrl: "/app/home/home.html"
            })
            .state('home.message', {
                url: "message/{messageId:.*}",
                templateUrl: "/app/home/home.html"
            })


            /** Editor **/
            .state('editor', {
                url: "/editor",
                templateUrl: "/app/editor/editor.html",
                data: { rolesRequired: ["editor", "admin", "sysadmin"] }
            })
            .state('editor.edit', {
                url: "/edit/:id",
                templateUrl: "/app/editor/editor-viewmode-edit.html"
            })
            .state('editor.copy', {
                url: "/edit/:id/:referenceType",
                templateUrl: "/app/editor/editor-viewmode-edit.html"
            })
            .state('editor.template', {
                url: "/edit/",
                templateUrl: "/app/editor/editor-viewmode-edit.html"
            })
            .state('editor.status', {
                url: "/status/:id",
                templateUrl: "/app/editor/editor-viewmode-status.html"
            })
            .state('editor.comments', {
                url: "/comments/:id",
                templateUrl: "/app/editor/editor-viewmode-comments.html"
            })
            .state('editor.history', {
                url: "/history/:id",
                templateUrl: "/app/editor/editor-viewmode-history.html"
            })


            /** Schedule **/
            .state('schedule', {
                url: "/schedule",
                templateUrl: "/app/schedule/schedule.html",
                data: { rolesRequired: ["editor", "admin", "sysadmin"] }
            })


            /** Messages **/
            .state('messages', {
                url: "/messages",
                templateUrl: "/app/messages/messages.html"
            })
            .state('messages.table', {
                url: "/table",
                templateUrl: "/app/messages/messages-viewmode-table.html"
            })
            .state('messages.grid', {
                url: "/grid",
                templateUrl: "/app/messages/messages-viewmode-grid.html"
            })
            .state('messages.map', {
                url: "/map",
                templateUrl: "/app/messages/messages-viewmode-map.html"
            })
            .state('messages.details', {
                url: "/details",
                templateUrl: "/app/messages/messages-viewmode-details.html"
            })
            .state('messages.selected', {
                url: "/selected",
                templateUrl: "/app/messages/messages-viewmode-selected.html"
            })



            /** AtoNs **/
            .state('atons', {
                url: "/atons",
                templateUrl: "/app/atons/atons.html"
            })
            .state('atons.grid', {
                url: "/grid",
                templateUrl: "/app/atons/atons-viewmode-grid.html"
            })
            .state('atons.map', {
                url: "/map",
                templateUrl: "/app/atons/atons-viewmode-map.html"
            })
            .state('atons.selected', {
                url: "/selected",
                templateUrl: "/app/atons/atons-viewmode-selected.html"
            })

                
            /** Admin **/
            .state('admin', {
                url: "/admin",
                templateUrl: "/app/admin/admin.html",
                data: { rolesRequired: [ "admin", "sysadmin" ] }
            })
            .state('admin.overview', {
                url: "/overview",
                templateUrl: "/app/admin/admin-page-overview.html"
            })
            .state('admin.charts', {
                url: "/charts",
                templateUrl: "/app/admin/admin-page-charts.html"
            })
            .state('admin.areas', {
                url: "/areas",
                templateUrl: "/app/admin/admin-page-areas.html"
            })
            .state('admin.categories', {
                url: "/categories",
                templateUrl: "/app/admin/admin-page-categories.html"
            })
            .state('admin.publications', {
                url: "/publications",
                templateUrl: "/app/admin/admin-page-publications.html"
            })
            .state('admin.sources', {
                url: "/sources",
                templateUrl: "/app/admin/admin-page-sources.html"
            })
            .state('admin.users', {
                url: "/users",
                templateUrl: "/app/admin/admin-page-users.html"
            })
            .state('admin.series', {
                url: "/series",
                templateUrl: "/app/admin/admin-page-series.html",
                data: { rolesRequired: [ "sysadmin" ] }
            })
            .state('admin.domains', {
                url: "/domains",
                templateUrl: "/app/admin/admin-page-domains.html",
                data: { rolesRequired: [ "sysadmin" ] }
            })
            .state('admin.schedules', {
                url: "/schedules",
                templateUrl: "/app/admin/admin-page-schedules.html",
                data: { rolesRequired: [ "sysadmin" ] }
            })
            .state('admin.integration', {
                url: "/integration",
                templateUrl: "/app/admin/admin-page-integration.html"
            })
            .state('admin.batch', {
                url: "/batch/:batchName",
                templateUrl: "/app/admin/admin-page-batch.html"
            })
            .state('admin.dictionaries', {
                url: "/dictionaries",
                templateUrl: "/app/admin/admin-page-dictionaries.html",
                data: { rolesRequired: [ "sysadmin" ] }
            })
            .state('admin.settings', {
                url: "/settings",
                templateUrl: "/app/admin/admin-page-settings.html",
                data: { rolesRequired: [ "sysadmin" ] }
            })

        ;
    }])


    .run(['$rootScope', '$window', '$state', '$location', 'growl', 'bowser', 'AuthService', 'AnalyticsService',
        function($rootScope, $window, $state, $location, growl, bowser, AuthService, AnalyticsService) {

        // Register if the user uses an IE browser - any IE version, but allow Edge for now
        $rootScope.msie = bowser.msie; // || bowser.msedge

        /** TODO: Merge with role checks in AuthCtrl and AuthService **/
        function checkRoles(roles) {
            for (var x = 0; x < roles.length; x++) {
                var role = roles[x];
                if (AuthService.keycloak.hasRealmRole(role) ||
                    ($rootScope.domain && AuthService.keycloak.hasResourceRole(role, $rootScope.domain.domainId)))
                    return true;
            }
            return false;
        }

        // If there are roles associated with any of the states, verify that the user has access, or log in
        $rootScope.$on('$stateChangeStart', function(event, next) {

            if (next.data && next.data.rolesRequired && !checkRoles(next.data.rolesRequired)) {
                event.preventDefault();
                if (AuthService.loggedIn) {
                    $state.go("home");
                    growl.error("Access Denied :-(", { ttl: 5000 });
                } else {
                    AuthService.login($location.absUrl());
                }
            }
        });

        // Configure Google Analytics
        if (AnalyticsService.enabled()) {

            // initialise google analytics
            AnalyticsService.initAnalytics();

            // track pageview on state change
            $rootScope.$on('$stateChangeSuccess', function () {
                AnalyticsService.logPageView();
            });
        }

    }]);


/** Bootstrap the Angular application **/
angular
    .element(document)
    .ready(function () {
        bootstrapKeycloak("niord.admin", 'check-sso');
    });
