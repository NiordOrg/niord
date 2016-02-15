/**
 * The main Niord message list app module
 *
 * @type {angular.Module}
 */

angular.module('niord.auth', []);
angular.module('niord.common', []);
angular.module('niord.conf', []);
angular.module('niord.map', []);
angular.module('niord.messages', []);


var app = angular.module('niord.atons', [
        'ngRoute', 'ngSanitize', 'ui.bootstrap', 'ui.select', 'pascalprecht.translate', 'angular-growl', 'ng-sortable',
        'niord.common', 'niord.auth', 'niord.conf', 'niord.map', 'niord.messages' ])

    .config(['$routeProvider', '$httpProvider', function ($routeProvider, $httpProvider) {
        'use strict';

        $httpProvider.interceptors.push('authHttpInterceptor');

        $routeProvider.when('/grid', {
            templateUrl: 'app/atons/atons-viewmode-grid.html',
            reloadOnSearch: false
        }).when('/map', {
            templateUrl: 'app/atons/atons-viewmode-map.html',
            reloadOnSearch: false
        }).when('/selected', {
            templateUrl: 'app/atons/atons-viewmode-selected.html',
            reloadOnSearch: false
        }).otherwise({
            redirectTo: '/grid'
        });
    }])

    .run(['$rootScope', function ($rootScope) {
        $rootScope.module = 'atons';
    }]);


/** Bootstrap the Angular application **/
angular
    .element(document)
    .ready(function () {
        bootstrapKeycloak("niord.atons", 'check-sso');
    });
