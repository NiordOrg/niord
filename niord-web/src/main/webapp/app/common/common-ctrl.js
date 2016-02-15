/**
 * The common controllers
 */
angular.module('niord.common')

    /**
     * Language Controller
     */
    .controller('LangCtrl', ['$scope', 'LangService',
        function ($scope, LangService) {
            'use strict';

            $scope.changeLanguage = function (lang) {
                LangService.changeLanguage(lang);
            }

        }])

    /**
     * Application Space Controller
     */
    .controller('AppSpaceCtrl', ['$scope', 'AppSpaceService',
        function ($scope, AppSpaceService) {
            'use strict';

            $scope.changeSpace = function (space) {
                AppSpaceService.changeAppSpace(space);
            }

        }]);
