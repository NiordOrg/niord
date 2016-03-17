/**
 * Common services.
 */
angular.module('niord.common')

    /**
     * The language service is used for changing language, etc.
     */
    .service('LangService', ['$rootScope', '$window', '$translate',
        function ($rootScope, $window, $translate) {
            'use strict';

            /** Change the current language settings */
            this.changeLanguage = function(lang) {
                $translate.use(lang);
                $rootScope.language = lang;
                $window.localStorage.lang = lang;
                moment.locale(lang);
                numeral.language($rootScope.numeralLauguages[lang]);
            };


            /** Updates the initial language settings when the application is loaded */
            this.initLanguage = function() {
                var language =
                    ($window.localStorage.lang && $.inArray($window.localStorage.lang, $rootScope.siteLanguages) > 0)
                        ? $window.localStorage.lang
                        : $rootScope.siteLanguages[0];

                this.changeLanguage(language);
            };

        }])


    /**
     * The application domain service is used for loading and changing application domains, etc.
     */
    .service('DomainService', ['$rootScope', '$window', '$http',
        function ($rootScope, $window, $http) {
            'use strict';

            var that = this;

            /** Changes the current application domain */
            this.changeDomain = function(domain) {
                if (domain) {
                    $rootScope.domain = domain;
                    $window.localStorage.domain = domain.id;
                } else  {
                    $rootScope.domain = undefined;
                    $window.localStorage.domain = undefined;
                }
                return domain;
            };

            /** Sets the list of domains as the current domains */
            function selectDomain(domains) {
                $rootScope.domains = domains;

                var domainId = $window.localStorage.domain;
                var matchingDomains = $.grep($rootScope.domains, function (domain) {
                    return domain.id == domainId;
                });
                var domain = matchingDomains.length == 1
                    ? matchingDomains[0]
                    : ($rootScope.domains ? $rootScope.domains[0] : undefined);
                that.changeDomain(domain);
            }

            /** Sets the initial application domain */
            this.initDomain = function () {
                // Start by setting the full list of domains
                selectDomain(niordDomains);
                // Once we have loaded the user specific domains, set these as the current list.
                $http.get('/rest/domains/user-domains').success(selectDomain);
            };

        }])


    /**
     * The modalService is very much inspired by (even copied from):
     * http://weblogs.asp.net/dwahlin/building-an-angularjs-modal-service
     */
    .service('DialogService', ['$uibModal',
        function ($uibModal) {
            'use strict';

            var modalDefaults = {
                backdrop: true,
                keyboard: true,
                modalFade: true,
                templateUrl: '/app/common/dialog.html'
            };

            var modalOptions = {
                closeButtonText: 'Cancel',
                actionButtonText: 'OK',
                headerText: '',
                bodyText: undefined
            };


            /** Display a dialog with the given options */
            this.showDialog = function (customModalDefaults, customModalOptions) {
                if (!customModalDefaults) {
                    customModalDefaults = {};
                }
                customModalDefaults.backdrop = 'static';
                return this.show(customModalDefaults, customModalOptions);
            };


            /** Displays a confimation dialog */
            this.showConfirmDialog = function (headerText, bodyText) {
                return this.showDialog(undefined, {headerText: headerText, bodyText: bodyText});
            };


            /** Opens the dialog with the given options */
            this.show = function (customModalDefaults, customModalOptions) {
                //Create temp objects to work with since we're in a singleton service
                var tempModalDefaults = {};
                var tempModalOptions = {};

                //Map angular-ui modal custom defaults to modal defaults defined in service
                angular.extend(tempModalDefaults, modalDefaults, customModalDefaults);

                //Map modal.html $scope custom properties to defaults defined in service
                angular.extend(tempModalOptions, modalOptions, customModalOptions);

                if (!tempModalDefaults.controller) {
                    tempModalDefaults.controller = function ($scope, $uibModalInstance) {
                        $scope.modalOptions = tempModalOptions;
                        $scope.modalOptions.ok = function (result) {
                            $uibModalInstance.close(result);
                        };
                        $scope.modalOptions.close = function () {
                            $uibModalInstance.dismiss('cancel');
                        };
                    }
                }

                return $uibModal.open(tempModalDefaults).result;
            };

    }]);


/** An implementation of a Map that preserves the order of the keys */
function Map() {
    this.keys = [];
    this.data = {};

    this.put = function (key, value) {
        if (this.data[key] == null) {
            this.keys.push(key);
        }
        this.data[key] = value;
    };

    this.get = function (key) {
        return this.data[key];
    };

    this.remove = function (key) {
        var index = this.keys.indexOf(key);
        if (index > -1) {
            this.keys.splice(index, 1);
        }
        delete this.data[key];
    };

    this.each = function (fn) {
        if (typeof fn != 'function') {
            return;
        }
        var len = this.keys.length;
        for (var i = 0; i < len; i++) {
            var key = this.keys[i];
            fn(key, this.data[key], i);
        }
    };

    this.entries = function () {
        var len = this.keys.length;
        var entries = new Array(len);
        for (var i = 0; i < len; i++) {
            var key = this.keys[i];
            entries[i] = {
                key: key,
                value: this.data[key]
            };
        }
        return entries;
    };

    this.isEmpty = function () {
        return this.keys.length == 0;
    };

    this.size = function () {
        return this.keys.length;
    };

    this.clear = function () {
        this.keys = [];
        this.data = {};
    }
}
