/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var module = angular.module('product', []);

var auth = {};


angular.element(document).ready(function ($http) {
    var keycloak = new Keycloak('keycloak.json');
    auth.loggedIn = false;

    keycloak.init({ onLoad: 'check-sso' }).success(function (authenticated) {
        console.log('*** LOGGED IN: ' + authenticated);
        auth.loggedIn = authenticated;
        auth.keycloak = keycloak;
        module.factory('AuthService', function() {
            return auth;
        });
        angular.bootstrap(document, ["product"]);
    }).error(function () {
            window.location.reload();
        });

});

module.controller('GlobalCtrl', function($scope, $http, AuthService) {
    $scope.userProfile = undefined;

    $scope.isLoggedIn = function () {
        return Auth.loggedIn;
    };

    $scope.login = function () {
        Auth.keycloak.login();
    };

    $scope.logout = function () {
        Auth.loggedIn = false;
        Auth.keycloak.logout();
    };

    $scope.loadUserProfile = function () {
        /*
        Auth.keycloak.loadUserProfile().success(function (userProfile) {
            console.log('*** Profile: ' + userProfile);
            $scope.userProfile = userProfile;
            $scope.$$phase || $scope.$apply();
        });
        */

        //$http.get('/rest/test/charts')
        //$http.get('/rest/atons/search?name=1133')
        $http.get('/rest/test/xxx')
            .success(function (data) {
                $scope.userProfile = data;
            })
            .error(function (err) {
                console.log('*** ERROR: ' + err);

            });
    }

});

module.factory('myHttpInterceptor', function($q, AuthService) {
    return {

        'request': function(config) {
            var deferred = $q.defer();

            if (Auth.keycloak.token) {
                Auth.keycloak.updateToken(5).success(function() {
                    config.headers = config.headers || {};
                    config.headers.Authorization = 'Bearer ' + Auth.keycloak.token;
                    deferred.resolve(config);
                }).error(function() {
                    deferred.reject('Failed to refresh token');
                });
            } else {
                // Not authenticated - leave it to the server to fail
                deferred.resolve(config);
            }
            return deferred.promise;
        },

        'responseError': function(response) {
            if (response.status == 401) {
                console.error('session timeout?');
                logout();
            } else if (response.status == 403) {
                console.error('Forbidden');
            } else if (response.status == 404) {
                console.error('Not found');
            } else if (response.status) {
                if (response.data && response.data.errorMessage) {
                    console.error(response.data.errorMessage);
                } else {
                    console.error("An unexpected server error has occurred " + response.status);
                }
            }
            return $q.reject(response);
        }
    };
});


module.config(function($httpProvider) {
   $httpProvider.interceptors.push('myHttpInterceptor');
});

