/**
 * Services that handles authentication and authorization via the backend
 */
angular.module('niord.auth')

    /**
     * Interceptor that adds a Keycloak access token to the requests as an authorization header.
     */
    .factory('authHttpInterceptor', function($q, Auth) {
        return {

            'request': function(config) {
                var deferred = $q.defer();

                if (Auth.keycloak.token) {
                    Auth.keycloak.updateToken(60).success(function() {
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
                    Auth.logout();
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
    })


    /**
     * Register global functions available on root scope
     */
    .run(['$rootScope', '$location',
        function ($rootScope, $location) {

            $rootScope.go = function (path) {
                $location.path(path);
            };
        }]);


var auth = {};

/**
 * Will bootstrap Keycloak and register the "Auth" service
 * @param angularAppName the angular modules
 */
function bootstrapKeycloak(angularAppName, onLoad) {
    var keycloak = new Keycloak('keycloak.json');
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

        // Register the Auth factory
        app.factory('Auth', function() {
            return auth;
        });

        angular.bootstrap(document, [ angularAppName ]);

    }).error(function () {
        window.location.reload();
    });

}

/**
 * Checks that the user has the given role. Otherwise, redirects to "/"
 * @param role the role to check
 */
checkRole = function (role) {
    return {
        load: function ($q, Auth) {

            if (role &&
                Auth.loggedIn &&
                (Auth.keycloak.hasRealmRole(role) || Auth.keycloak.hasResourceRole(role))) {
                var deferred = $q.defer();
                deferred.resolve();
                return deferred.promise;
            }

            console.error("User must have role " + role + ". Redirecting to front page");
            location.href = "/";
        }
    }
};

