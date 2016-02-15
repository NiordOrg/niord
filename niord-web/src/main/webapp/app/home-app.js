/**
 * The main Niord front page app module
 *
 * @type {angular.Module}
 */

angular.module('niord.auth', []);
angular.module('niord.common', []);
angular.module('niord.conf', []);
angular.module('niord.map', []);
angular.module('niord.messages', []);


var app = angular.module('niord.home', [
        'ngRoute', 'ngSanitize', 'ui.bootstrap', 'pascalprecht.translate',
        'niord.common', 'niord.auth', 'niord.conf', 'niord.map', 'niord.messages' ])

    .config(['$routeProvider', '$httpProvider', function ($routeProvider, $httpProvider) {
        'use strict';

        $httpProvider.interceptors.push('authHttpInterceptor');

        $routeProvider.when('/', {
            templateUrl: 'app/home/home.html'
        }).otherwise({
            templateUrl: 'app/home/home.html'
        });
    }])

    .run(['$rootScope', function ($rootScope) {
        $rootScope.module = 'home';
    }]);


/** Bootstrap the Angular application **/
angular
    .element(document)
    .ready(function () {
        bootstrapKeycloak("niord.home", 'check-sso');
    });

