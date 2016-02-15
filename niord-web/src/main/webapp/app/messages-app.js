/**
 * The main Niord message list app module
 *
 * @type {angular.Module}
 */

angular.module('niord.auth', []);
angular.module('niord.common', []);
angular.module('niord.conf', []);
angular.module('niord.map', []);
angular.module('niord.atons', []);


var app = angular.module('niord.messages', [
        'ngRoute', 'ngSanitize', 'ui.bootstrap', 'ui.select', 'pascalprecht.translate', 'angular-growl',
        'niord.common', 'niord.auth', 'niord.conf', 'niord.map', 'niord.atons' ])

    .config(['$routeProvider', '$httpProvider', function ($routeProvider, $httpProvider) {
        'use strict';

        $httpProvider.interceptors.push('authHttpInterceptor');

        $routeProvider.when('/grid', {
            templateUrl: 'app/messages/messages-viewmode-grid.html',
            reloadOnSearch: false
        }).when('/map', {
            templateUrl: 'app/messages/messages-viewmode-map.html',
            reloadOnSearch: false
        }).when('/details', {
            templateUrl: 'app/messages/messages-viewmode-details.html',
            reloadOnSearch: false
        }).when('/table', {
            templateUrl: 'app/messages/messages-viewmode-table.html',
            reloadOnSearch: false
        }).otherwise({
            redirectTo: '/grid'
        });
    }])

    .run(['$rootScope', function ($rootScope) {
        $rootScope.module = 'messages';
    }]);


/** Bootstrap the Angular application **/
angular
    .element(document)
    .ready(function () {
        bootstrapKeycloak("niord.messages", 'check-sso');
    });

