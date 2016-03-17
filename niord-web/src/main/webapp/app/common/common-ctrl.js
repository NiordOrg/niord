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
     * Domain Controller
     */
    .controller('DomainCtrl', ['$scope', 'DomainService',
        function ($scope, DomainService) {
            'use strict';

            $scope.changeDomain = function (domain) {
                DomainService.changeDomain(domain);
            }

        }]);
