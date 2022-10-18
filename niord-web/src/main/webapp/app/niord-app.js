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
angular.module('niord.template', []);
angular.module('niord.common', []);
angular.module('niord.conf', []);
angular.module('niord.map', []);
angular.module('niord.editor', []);
angular.module('niord.schedule', []);
angular.module('niord.home', []);


var app = angular.module('niord.admin', [
        'ngSanitize', 'ui.bootstrap', 'ui.select', 'ui.router', 'ui.tinymce', 'ui.mask', 'ui.ace',
        'pascalprecht.translate', 'angular-growl', 'ng-sortable', 'angularFileUpload','jlareau.bowser', 'remoteValidation',
        'niord.common', 'niord.auth', 'niord.admin', 'niord.atons', 'niord.messages', 'niord.template',
        'niord.conf', 'niord.map', 'niord.editor', 'niord.schedule', 'niord.home' ])

    .config(['$stateProvider', '$urlRouterProvider', '$httpProvider', function ($stateProvider, $urlRouterProvider, $httpProvider) {
        'use strict';

        $httpProvider.interceptors.push('authHttpInterceptor');


        $urlRouterProvider
            .when('/messages', '/messages/table')
            .when('/atons', '/atons/grid')
            .when('/editor', '/editor/edit/')
            .when('/admin', '/admin/overview')
            .when('/sysadmin', '/sysadmin/overview')
            .otherwise("/");

        $stateProvider

            /** Home **/
            .state('home', {
                url: "/",
                templateUrl: "/app/home/home.html",
                help: { section: 'home' }
            })
            .state('home.message', {
                url: "message/{messageId:.*}",
                templateUrl: "/app/home/home.html"
            })


            /** Editor **/
            .state('editor', {
                url: "/editor",
                templateUrl: "/app/editor/editor.html",
                data: { rolesRequired: ["user", "editor", "admin", "sysadmin"] }
            })
            .state('editor.edit', {
                url: "/edit/:id",
                templateUrl: "/app/editor/editor-viewmode-edit.html",
                help: { section: 'editor' }
            })
            .state('editor.copy', {
                url: "/edit/:id/:referenceType",
                templateUrl: "/app/editor/editor-viewmode-edit.html",
                help: { section: 'editor' }
            })
            .state('editor.template', {
                url: "/edit/",
                templateUrl: "/app/editor/editor-viewmode-edit.html",
                help: { section: 'editor' }
            })
            .state('editor.status', {
                url: "/status/:id",
                templateUrl: "/app/editor/editor-viewmode-status.html",
                help: { section: 'status-editor-page' }
            })
            .state('editor.comments', {
                url: "/comments/:id",
                templateUrl: "/app/editor/editor-viewmode-comments.html",
                help: { section: 'comments-editor-page' }
            })
            .state('editor.history', {
                url: "/history/:id",
                templateUrl: "/app/editor/editor-viewmode-history.html",
                help: { section: 'history-editor-page' }
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
                templateUrl: "/app/messages/messages-viewmode-table.html",
                help: { section: 'messages' }
            })
            .state('messages.grid', {
                url: "/grid",
                templateUrl: "/app/messages/messages-viewmode-grid.html",
                help: { section: 'messages' }
            })
            .state('messages.map', {
                url: "/map",
                templateUrl: "/app/messages/messages-viewmode-map.html",
                help: { section: 'messages' }
            })
            .state('messages.details', {
                url: "/details",
                templateUrl: "/app/messages/messages-viewmode-details.html",
                help: { section: 'messages' }
            })
            .state('messages.selected', {
                url: "/selected",
                templateUrl: "/app/messages/messages-viewmode-selected.html",
                help: { section: 'messages' }
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
                templateUrl: "/app/admin/admin-page-overview.html",
                help: { manual: 'admin', section: '' }
            })
            .state('admin.charts', {
                url: "/charts",
                templateUrl: "/app/admin/admin-page-charts.html",
                help: { manual: 'admin', section: 'charts' }
            })
            .state('admin.areas', {
                url: "/areas",
                templateUrl: "/app/admin/admin-page-areas.html",
                help: { manual: 'admin', section: 'areas' }
            })
            .state('admin.publications', {
                url: "/publications",
                templateUrl: "/app/admin/admin-page-publications.html",
                help: { manual: 'admin', section: 'publications' }
            })
            .state('admin.sources', {
                url: "/sources",
                templateUrl: "/app/admin/admin-page-sources.html",
                help: { manual: 'admin', section: 'sources' }
            })
            .state('admin.users', {
                url: "/users",
                templateUrl: "/app/admin/admin-page-users.html",
                help: { manual: 'admin', section: 'users-contacts' }
            })
            .state('admin.mailing-lists', {
                url: "/mailing-lists",
                templateUrl: "/app/admin/admin-page-mailing-lists.html",
                help: { manual: 'admin', section: 'mailing-lists' }
            })


            /** Sysadmin **/
            .state('sysadmin', {
                url: "/sysadmin",
                templateUrl: "/app/sysadmin/sysadmin.html",
                data: { rolesRequired: [ "sysadmin" ] }
            })
            .state('sysadmin.overview', {
                url: "/overview",
                templateUrl: "/app/sysadmin/sysadmin-page-overview.html",
                help: { manual: 'sysadmin', section: '' }
            })
            .state('sysadmin.series', {
                url: "/series",
                templateUrl: "/app/sysadmin/sysadmin-page-series.html",
                help: { manual: 'sysadmin', section: 'message-series' }
            })
            .state('sysadmin.domains', {
                url: "/domains",
                templateUrl: "/app/sysadmin/sysadmin-page-domains.html",
                help: { manual: 'sysadmin', section: 'domains' }
            })
            .state('sysadmin.promulgation', {
                url: "/promulgation",
                templateUrl: "/app/sysadmin/sysadmin-page-promulgation.html",
                help: { manual: 'sysadmin', section: 'promulgation-types' }
            })
            .state('sysadmin.schedules', {
                url: "/schedules",
                templateUrl: "/app/sysadmin/sysadmin-page-schedules.html",
                help: { manual: 'sysadmin', section: 'firing-schedules' }
            })
            .state('sysadmin.dictionaries', {
                url: "/dictionaries",
                templateUrl: "/app/sysadmin/sysadmin-page-dictionaries.html",
                help: { manual: 'sysadmin', section: 'dictionaries' }
            })
            .state('sysadmin.param-types', {
                url: "/param-types",
                templateUrl: "/app/sysadmin/sysadmin-page-param-types.html",
                help: { manual: 'sysadmin', section: 'parameter-types' }
            })
            .state('sysadmin.script-resources', {
                url: "/script-resources/:path",
                templateUrl: "/app/sysadmin/sysadmin-page-script-resources.html",
                help: { manual: 'sysadmin', section: 'script-resources' }
            })
            .state('sysadmin.categories', {
                url: "/categories",
                templateUrl: "/app/sysadmin/sysadmin-page-categories.html",
                help: { manual: 'sysadmin', section: 'categories' }
            })
            .state('sysadmin.reports', {
                url: "/reports",
                templateUrl: "/app/sysadmin/sysadmin-page-reports.html",
                help: { manual: 'sysadmin', section: 'print-reports' }
            })
            .state('sysadmin.mails', {
                url: "/mails",
                templateUrl: "/app/sysadmin/sysadmin-page-mails.html",
                help: { manual: 'sysadmin', section: 'mail-queue' }
            })
            .state('sysadmin.settings', {
                url: "/settings",
                templateUrl: "/app/sysadmin/sysadmin-page-settings.html",
                help: { manual: 'sysadmin', section: 'settings' }
            })
            .state('sysadmin.niord-integration', {
                url: "/niord-integration",
                templateUrl: "/app/sysadmin/sysadmin-page-niord-integration.html"
            })
            .state('sysadmin.integration', {
                url: "/integration",
                templateUrl: "/app/sysadmin/sysadmin-page-integration.html",
                help: { manual: 'sysadmin', section: 'integration' }
            })
            .state('sysadmin.batch', {
                url: "/batch/:batchName",
                templateUrl: "/app/sysadmin/sysadmin-page-batch.html",
                help: { manual: 'sysadmin', section: 'batch-jobs' }
            })
        ;
    }])


    .run(['$rootScope', '$state', '$location', 'growl', 'bowser', 'AuthService',
        function($rootScope, $state, $location, growl, bowser, AuthService) {

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

    }]);


/** Bootstrap the Angular application **/
angular
    .element(document)
    .ready(function () {
        bootstrapKeycloak("niord.admin", 'check-sso');
    });
