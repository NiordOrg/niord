/**
 * The common controllers
 */
angular.module('niord.common')

    /**
     * Language Controller
     */
    .controller('LangCtrl', ['$scope', '$window', 'LangService',
        function ($scope, $window, LangService) {
            'use strict';

            $scope.changeLanguage = function (lang) {
                LangService.changeLanguage(lang);
                $window.location.reload();
            }

        }])

    /**
     * Domain Controller
     */
    .controller('DomainCtrl', ['$scope', '$window', 'DomainService',
        function ($scope, $window, DomainService) {
            'use strict';

            $scope.changeDomain = function (domain) {
                DomainService.changeDomain(domain);
                $window.location.reload();
            }

        }]);
