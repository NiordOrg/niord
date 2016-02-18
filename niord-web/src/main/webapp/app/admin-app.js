/**
 * The main Niord message list app module
 *
 * @type {angular.Module}
 */

angular.module('niord.auth', []);
angular.module('niord.common', []);
angular.module('niord.conf', []);
angular.module('niord.map', []);


var app = angular.module('niord.admin', [
        'ngRoute', 'ngSanitize', 'ui.bootstrap', 'ui.select', 'pascalprecht.translate', 'angular-growl', 'angularFileUpload',
        'niord.common', 'niord.auth', 'niord.conf', 'niord.map' ])

    .config(['$routeProvider', '$httpProvider', function ($routeProvider, $httpProvider) {
        'use strict';

        $httpProvider.interceptors.push('authHttpInterceptor');

        $routeProvider.when('/overview', {
            templateUrl: 'app/admin/admin-page-overview.html',
            reloadOnSearch: false
        }).when('/charts', {
            templateUrl: 'app/admin/admin-page-charts.html',
            reloadOnSearch: false
        }).when('/areas', {
            templateUrl: 'app/admin/admin-page-areas.html',
            reloadOnSearch: false
        }).when('/integration', {
            templateUrl: 'app/admin/admin-page-integration.html',
            reloadOnSearch: false
        }).otherwise({
            redirectTo: '/overview'
        });
    }])

    .run(['$rootScope', function ($rootScope) {
        $rootScope.module = 'admin';
    }]);


/** Bootstrap the Angular application **/
angular
    .element(document)
    .ready(function () {
        bootstrapKeycloak("niord.admin", 'login-required');
    });
