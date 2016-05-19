
/**
 * The Auth controller. Install it at all html pages that require authentication/authorization
 */
angular.module('niord.auth')
    .controller('AuthCtrl', ['$scope', '$rootScope', 'AuthService',
        function ($scope, $rootScope, AuthService) {
            'use strict';

            $scope.isLoggedIn = AuthService.loggedIn;

            /** Returns the user name ,**/
            $scope.userName = function () {
                if (AuthService.keycloak.idTokenParsed) {
                    return AuthService.keycloak.idTokenParsed.name 
                        || AuthService.keycloak.idTokenParsed.preferred_username;
                }
                return undefined;
            };
            $scope.userName = $scope.userName();

            /** Logs the user in via Keycloak **/
            $scope.login = function () {
                AuthService.login();
            };

            /** Logs the user out via Keycloak **/
            $scope.logout = function () {
                AuthService.logout();
            };

            /** Enters the Keycloak account management **/
            $scope.accountManagement = function () {
                AuthService.keycloak.accountManagement();
            };

            /** Returns if the user has the given role in Keycloak **/
            $scope.hasRole = $rootScope.hasRole;

        }]);
