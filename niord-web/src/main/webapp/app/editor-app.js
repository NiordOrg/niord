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


var app = angular.module('niord.editor', [
        'ngRoute', 'ngSanitize', 'ui.bootstrap', 'ui.select', 'pascalprecht.translate', 'angular-growl',
        'niord.common', 'niord.auth', 'niord.conf', 'niord.map', 'niord.atons' ])

    .config(['$routeProvider', '$httpProvider', function ($routeProvider, $httpProvider) {
        'use strict';

        $httpProvider.interceptors.push('authHttpInterceptor');

        $routeProvider.when('/new-message', {
            templateUrl: 'app/editor/editor.html'
        }).otherwise({
            redirectTo: '/new-message'
        });
    }])

    .run(['$rootScope', function ($rootScope) {
        $rootScope.module = 'editor';
    }]);


/** Bootstrap the Angular application **/
angular
    .element(document)
    .ready(function () {
        bootstrapKeycloak("niord.editor", 'login-required');
    });
