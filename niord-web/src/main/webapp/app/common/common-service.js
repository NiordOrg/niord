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
 * Common services.
 */
angular.module('niord.common')

    /**
     * The language service is used for changing language, etc.
     */
    .service('LangService', ['$rootScope', '$window', '$translate',
        function ($rootScope, $window, $translate) {
            'use strict';

            /** Returns the current language **/
            this.language = function () {
                return $rootScope.language;
            };


            /** Change the current language settings */
            this.changeLanguage = function(lang) {
                $translate.use(lang);
                $rootScope.language = lang;
                $window.localStorage.lang = lang;
                moment.locale(lang);
                numeral.language($rootScope.numeralLanguages[lang]);
            };


            /** Updates the initial language settings when the application is loaded */
            this.initLanguage = function() {
                var language =
                    ($window.localStorage.lang && $.inArray($window.localStorage.lang, $rootScope.siteLanguages) > 0)
                        ? $window.localStorage.lang
                        : $rootScope.siteLanguages[0];

                this.changeLanguage(language);
            };


            /** Translates the given key **/
            this.translate = function (key, params, language) {
                language = language || $rootScope.language;
                return $translate.instant(key, params, null, language)
            };


            /** look for a description entity with the given language */
            this.descForLanguage = function(elm, lang) {
                if (elm && elm.descs) {
                    for (var l = 0; l < elm.descs.length; l++) {
                        if (elm.descs[l].lang === lang) {
                            return elm.descs[l];
                        }
                    }
                }
                return undefined;
            };


            /** look for a description entity with the given language - falls back to using the first description */
            this.descForLangOrDefault = function(elm, lang) {
                lang = lang || $rootScope.language;
                var desc = this.descForLanguage(elm, lang);
                if (!desc && elm && elm.descs && elm.descs.length > 0) {
                    desc = elm.descs[0];
                }
                return desc;
            };


            /** look for a description entity with the current language **/
            this.desc = function(elm) {
                return this.descForLanguage(elm, $rootScope.language);
            };


            /**
             * Ensures that elm.descs contain a description entity for each supported language.
             *
             * The initFunc will be called for newly added description entities and should be used
             * to initialize the fields to include, e.g. "description" or "name",...
             *
             * Optionally, an oldElm can be specified, from which the description entity will be picked
             * if present
             */
            this.checkDescs = function (elm, initFunc, oldElm, languages) {
                if (!elm.descs) {
                    elm.descs = [];
                }
                if (!languages || languages.length === 0) {
                    languages = $rootScope.modelLanguages;
                }
                for (var l = 0; l < languages.length; l++) {
                    var lang = languages[l];
                    var desc = this.descForLanguage(elm, lang);
                    if (!desc && oldElm) {
                        desc = this.descForLanguage(oldElm, lang);
                        if (desc) {
                            elm.descs.push(desc);
                        }
                    }
                    if (!desc) {
                        desc = { 'lang': lang };
                        initFunc(desc);
                        elm.descs.push(desc);
                    }
                }
                // Lastly, sort by language
                this.sortDescs(elm);
                return elm;
            };


            /**
             * Computes a sort value by comparing the desc language to the
             * current language or else the index in the list of available languages.
             */
            function sortValue(desc, lang) {
                lang = lang || $rootScope.language;
                if (!desc.lang){
                    return 1000;
                } else if (desc.lang === lang) {
                    return -1;
                }
                var index = $.inArray(desc, lang);
                return (index === -1) ? 999 : index;
            }

            
            /** Sort the localized description entities by language */
            this.sortDescs = function (elm, lang) {
                if (elm && elm.descs) {
                    elm.descs.sort(function(d1, d2){
                        return sortValue(d1, lang) - sortValue(d2, lang);
                    });
                }
                return elm;
            };


            /** Sorts all the localizable description entities of the message by language **/
            this.sortMessageDescs = function (msg, lang) {
                var that = this;
                lang = lang || $rootScope.language;
                if (msg) {
                    this.sortDescs(msg, lang);
                    if (msg.parts) {
                        angular.forEach(msg.parts, function (part) {
                            that.sortDescs(part, lang);
                        });
                    }
                    if (msg.attachments) {
                        angular.forEach(msg.attachments, function (att) {
                            that.sortDescs(att, lang);
                        });
                    }
                    if (msg.references) {
                        angular.forEach(msg.references, function (ref) {
                            that.sortDescs(ref, lang);
                        });
                    }
                }
                return msg;
            };


            /**
             * Recursively formats the names of the parent lineage for areas and categories
             **/
            this.formatParents = function(child) {
                var txt = undefined;
                if (child) {
                    txt = (child.descs && child.descs.length > 0) ? child.descs[0].name : 'N/A';
                    if (child.parent) {
                        txt = this.formatParents(child.parent) + " - " + txt;
                    }
                }
                return txt;
            };

        }])


    /**
     * The application domain service is used for loading and changing application domains, etc.
     */
    .service('DomainService', ['$rootScope', '$window', '$location', 'AuthService',
        function ($rootScope, $window, $location, AuthService) {
            'use strict';

            var that = this;

            /** Changes the current application domain */
            this.changeDomain = function(domain) {
                if (domain) {
                    $rootScope.domain = domain;
                    $window.localStorage.domain = domain.domainId;
                    if (domain.timeZone) {
                        moment.tz.setDefault(domain.timeZone);
                    }
                } else  {
                    $rootScope.domain = undefined;
                    $window.localStorage.removeItem('domain');
                }
                return domain;
            };

            /** Sets the initial application domain */
            this.initDomain = function () {

                // Mark domains editable if the current user has a role associated with them
                angular.forEach($rootScope.domains, function (domain) {
                    domain.editable = AuthService.hasRolesFor(domain.domainId);
                });

                var requestDomain = $location.search().domain;
                var domainId = requestDomain || $window.localStorage.domain;
                var matchingDomains = $.grep($rootScope.domains, function (domain) {
                    return domain.domainId === domainId;
                });
                var domain = matchingDomains.length === 1
                    ? matchingDomains[0]
                    : ($rootScope.domains ? $rootScope.domains[0] : undefined);
                that.changeDomain(domain);
            };

        }])


    /**
     * Interface for logging to Google Analytics
     */
    .factory('AnalyticsService', [ '$rootScope', '$window', '$location',
        function($rootScope, $window, $location) {
            'use strict';

            function gaEnabled() {
                return $rootScope.analyticsTrackingId && $rootScope.analyticsTrackingId.length > 0;
            }

            return {

                /** Returns if Google Analytics is enabled or not **/
                enabled: function () {
                    return gaEnabled();
                },


                /** Initializes Google Analytics **/
                initAnalytics: function () {
                    // initialise google analytics
                    if (gaEnabled()) {
                        try {
                            $window.ga('create', $rootScope.analyticsTrackingId, 'auto');
                        } catch (ex) {
                        }
                    }
                },


                /** Logs the given page view **/
                logPageView: function (page) {
                    if (gaEnabled()) {
                        try {
                            page = page || $location.path();
                            $window.ga('send', 'pageview', page);
                        } catch (ex) {
                        }
                    }
                },


                /** Logs the given event **/
                logEvent: function (category, action, label, value) {
                    if (gaEnabled()) {
                        try {
                            $window.ga('send', 'event', category, action, label, value);
                        } catch (ex) {
                        }
                    }
                }
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

        }])

    /**
     * Service for uploading files
     */
    .service('UploadFileService', ['$uibModal',
        function ($uibModal) {
            'use strict';

            /** Opens an upload file dialog **/
            this.showUploadFileDialog = function (title, uploadUrl, fileTypes, closeAfterUpload) {

                return $uibModal.open({
                    templateUrl: '/app/common/file-upload-dialog.html',
                    controller: function ($scope) {
                        $scope.title = title;
                        $scope.uploadUrl = uploadUrl;
                        $scope.fileTypes = fileTypes;
                        $scope.importResult = '';

                        $scope.fileUploaded = function(result) {
                            if (closeAfterUpload) {
                                $scope.$close(result);
                                $scope.$$phase || $scope.$apply();
                            } else {
                                $scope.importResult = result;
                                $scope.$$phase || $scope.$apply();
                            }
                        };

                        $scope.fileUploadError = function(status) {
                            $scope.importResult = "Error importing charts (error " + status + ")";
                            $scope.$$phase || $scope.$apply();
                        };
                    },
                    size: 'md'
                });
            }

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

    this.values = function () {
        var len = this.keys.length;
        var values = new Array(len);
        for (var i = 0; i < len; i++) {
            var key = this.keys[i];
            values[i] = this.data[key]
        }
        return values;
    };

    this.isEmpty = function () {
        return this.keys.length === 0;
    };

    this.size = function () {
        return this.keys.length;
    };

    this.clear = function () {
        this.keys = [];
        this.data = {};
    }
}
