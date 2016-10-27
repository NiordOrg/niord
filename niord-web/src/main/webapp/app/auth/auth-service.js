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
 * Services that handles authentication and authorization via the backend
 */
angular.module('niord.auth')

    /**
     * Interceptor that adds a Keycloak access token to the requests as an authorization header.
     */
    .factory('authHttpInterceptor', ['$rootScope', '$q', 'AuthService',
        function($rootScope, $q, AuthService) {
            return {

                'request': function(config) {
                    var deferred = $q.defer();
                    config.headers = config.headers || {};

                    if ($rootScope.domain) {
                        config.headers['NiordDomain'] = $rootScope.domain.domainId;
                    }

                    if (AuthService.keycloak.token) {
                        AuthService.keycloak.updateToken(60).success(function() {
                            config.headers.Authorization = 'Bearer ' + AuthService.keycloak.token;
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
                        AuthService.logout();
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
                    } else if (response == "Failed to refresh token") {
                        AuthService.logout();
                    }
                    return $q.reject(response);
                }
            };
        }])


    /**
     * Register global functions available on root scope
     */
    .run(['$rootScope', '$location', 'AuthService',
        function ($rootScope, $location, AuthService) {

            // Navigate to the given path
            $rootScope.go = function (path) {
                $location.path(path);
            };


            /** Returns if the user has the given role in Keycloak **/
            $rootScope.hasRole = function (role) {
                return AuthService.keycloak.hasRealmRole(role) ||
                    ($rootScope.domain && AuthService.keycloak.hasResourceRole(role, $rootScope.domain.domainId));
            };


            /** Returns if the user has the given resource role in Keycloak **/
            $rootScope.hasResourceRole = function (role, resourceId) {
                return AuthService.keycloak.hasResourceRole(role, resourceId);
            };


            /** Returns if the user supports the given message mainType in the current domain **/
            $rootScope.supportsMainType = function(mainType) {
                if ($rootScope.domain && $rootScope.domain.messageSeries) {
                    var mainTypeSeries = $.grep($rootScope.domain.messageSeries, function (series) {
                        return series.mainType == mainType;
                    });
                    return mainTypeSeries.length > 0;
                }
                return false;
            };
            

            /** Returns if the user is logged in **/
            $rootScope.isLoggedIn = AuthService.loggedIn;


            /** Create a global selection cache for messages and AtoNs **/
            $rootScope.messageSelection = new Map();
            $rootScope.atonSelection = new Map();
            $rootScope.templateMessage = {
                mainType: undefined,
                geometry: undefined,
                descs: []
            }

        }]);


var auth = {};

/**
 * Will bootstrap Keycloak and register the "Auth" service
 * @param angularAppName the angular modules
 */
function bootstrapKeycloak(angularAppName, onLoad) {
    var keycloak = new Keycloak('/conf/keycloak.json');
    auth.loggedIn = false;

    var initProps = {};
    if (onLoad) {
        initProps.onLoad = onLoad;
    }

    keycloak.init(
        initProps
    ).success(function (authenticated) {

        auth.loggedIn = authenticated;
        auth.keycloak = keycloak;

        // Returns whether the current user has any roles defined for the given domain
        auth.hasRolesFor = function (domainId) {
            if (!keycloak.resourceAccess) {
                return false;
            }
            var access = keycloak.resourceAccess[domainId];
            return !!access && access.roles.length > 0;
        };
        
        
        // Performs a log-in and sets the return url to the given value
        auth.login = function (returnUrl) {
            var loginOpts = {};
            if (returnUrl) {
                loginOpts.redirectUri = returnUrl;
            }
            keycloak.login(loginOpts);
        };


        // Logs out Keycloak
        auth.logout = function () {
            keycloak.logout();
            auth.loggedIn = false;
        };

        /** Returns the users full name, if defined, and otherwise the preferred username **/
        auth.userName = function () {
            if (keycloak.idTokenParsed) {
                return keycloak.idTokenParsed.name
                    || keycloak.idTokenParsed.preferred_username;
            }
            return undefined;
        };

        /** Returns the preferred username **/
        auth.preferredUsername = function () {
            if (keycloak.idTokenParsed) {
                return keycloak.idTokenParsed.preferred_username;
            }
            return undefined;
        };

        // Register the Auth factory
        app.factory('AuthService', function() {
            return auth;
        });

        angular.bootstrap(document, [ angularAppName ]);

    }).error(function () {
        window.location.reload();
    });

}
