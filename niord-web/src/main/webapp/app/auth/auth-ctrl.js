
/**
 * The Auth controller. Install it at all html pages that require authentication/authorization
 */
angular.module('niord.auth')
    .controller('AuthCtrl', ['$scope', 'Auth',
        function ($scope, Auth) {
            'use strict';

            $scope.isLoggedIn = Auth.loggedIn;

            /** Returns the user name ,**/
            $scope.userName = function () {
                if (Auth.keycloak.idTokenParsed) {
                    return Auth.keycloak.idTokenParsed.name || Auth.keycloak.idTokenParsed.preferred_username;
                }
                return undefined;
            };
            $scope.userName = $scope.userName();

            /** Logs the user in via Keycloak **/
            $scope.login = function () {
                Auth.keycloak.login();
            };

            /** Logs the user out via Keycloak **/
            $scope.logout = function () {
                Auth.keycloak.logout();
                Auth.loggedIn = false;
                Auth.keycloak = null;
            };

            /** Enters the Keycloak account management **/
            $scope.accountManagement = function () {
                Auth.keycloak.accountManagement();
            };

            /** Returns if the user has the given role in Keycloak **/
            $scope.hasRole = function (role) {
                return Auth.keycloak.hasRealmRole(role) ||
                    Auth.keycloak.hasResourceRole(role);
            }

        }]);
