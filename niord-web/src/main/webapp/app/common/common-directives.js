/**
 * Common directives.
 */
angular.module('niord.common')


    .directive('focus', ['$timeout', function ($timeout) {
        'use strict';

        return function (scope, element, attrs) {
            scope.$watch(attrs.focus, function (newValue) {
                $timeout(function () {
                    newValue && element.focus();
                }, 100);
            }, true);
        };
    }])


    /**
     * Show element active/inactive depending on the current location.
     * Usage:
     * <pre>
     *     <li check-active="/search/*"><a href="search.html">Search</a></li>
     * </pre>
     * <p>
     * Inspired by:
     *   http://stackoverflow.com/questions/16199418/how-do-i-implement-the-bootstrap-navbar-active-class-with-angular-js
     * - but changed quite a bit.
     */
    .directive('checkActive', [ '$location', '$window', function ($location, $window) {
        'use strict';

        return {
            restrict: 'A',
            scope: {
                checkActive: "@"
            },
            link: function (scope, element, attrs) {

                // Watch for the $location
                scope.$watch(function () {
                    return $location.path();
                }, function (newValue, oldValue) {

                    var locMask = scope.checkActive.split("*").join(".*");
                    var regexp = new RegExp('^' + locMask + '$', ['i']);

                    if (regexp.test(newValue)) {
                        element.addClass('active');
                    } else {
                        element.removeClass('active');
                    }
                });
            }
        };

    }])

    /**
     * Emits a flag image
     */
    .directive('flag', [function () {
        return {
            restrict: 'E',
            template: "<img height='16'/>",
            replace: true,
            scope: {
                lang: "=",
                style: "@"
            },
            link: function(scope, element, attrs) {
                scope.$watch(function() {
                        return scope.lang;
                    },
                    function(newValue) {
                        if (newValue) {
                            element.attr('src', '/img/flags/' + newValue + '.png');
                        }
                    }, true);

                if (scope.style) {
                    element.attr('style', scope.style);
                }
            }
        };
    }])


    .directive('bgFlag', [function () {
        return {
            restrict: 'A',
            scope: {
                bgFlag: "="
            },
            link: function(scope, element) {
                element.addClass("localized");
                element.css({
                    background: "white url('/img/flags/" + scope.bgFlag + ".png') no-repeat 99% 0%",
                    backgroundSize: "auto 14px"
                });
            }
        };
    }])


    /**
     * Defines a view mode filter panel
     */
    .directive('viewModeFilterPanel', [function () {
        return {
            restrict: 'E',
            templateUrl: '/app/common/view-mode-filter-panel.html',
            replace: true,
            transclude: true,
            scope: {
                class: '@',
                state: "=",
                filterName: "@",
                closable: '@',
                clearFilter: '&'
            },
            link: function(scope) {
                scope.closable = scope.closable || 'true';
                scope.close = function () {
                    if (scope.closable == 'true' && scope.clearFilter) {
                        scope.clearFilter({name: scope.filterName})
                    }
                }
            }
        };
    }])


    /**
     * Date-time picker based on:
     * http://eonasdan.github.io/bootstrap-datetimepicker/
     */
    .directive('dateTimePicker', ['$rootScope', function($rootScope) {
        return {
            require : '^ngModel',
            restrict : 'AE',
            replace : true,
            scope: {
                id: '@',
                format: '@',
                placeholder: '@'
            },
            template : '<div class="input-group date">'
                     + '  <input type="text" class="input-sm form-control" />'
                     + '  <span class="input-group-addon">'
                     + '    <span class="glyphicon glyphicon-calendar"></span>'
                     + '  </span>'
                     + '</div>',

            link : function(scope, element, attrs, ctrl) {

                var locale = $rootScope.language;

                var format = "DD/MM/YYYY HH:mm";
                if (scope.format) {
                    format = scope.format;
                }

                var input = element.find("input");
                input.attr('data-date-format', format);

                if (scope.id) {
                    $(element).attr('id', scope.id);
                }

                if (scope.placeholder) {
                    input.attr('placeholder', scope.placeholder);
                }

                var picker = $(element).datetimepicker({
                        locale : locale,
                        allowInputToggle : true,
                        useCurrent : true,
                        showTodayButton : true,
                        showClear : true
                    }).data('DateTimePicker');

                ctrl.$formatters.push(function(modelValue) {
                    var date;
                    if (!modelValue) {
                        date = null;
                        picker.date(null);
                    } else {
                        date = modelValue;
                        picker.date(moment(date));
                    }
                    return date
                });

                ctrl.$parsers.push(function(valueFromInput) {
                    if (!picker.date()) {
                        return null;
                    }
                    return picker.date().valueOf();
                });

                element.bind('blur change dp.change dp.hide', function() {
                    var millis = null;
                    var date = picker.date();
                    if (date) {
                        millis = date.valueOf();
                    }

                    ctrl.$setViewValue(millis);
                    ctrl.$modelValue = millis;
                    ctrl.$render();

                    $rootScope.$$phase || $rootScope.$apply();
                });
            }
        };
    }])

    /**
     * File upload, based on:
     * https://github.com/nervgh/angular-file-upload
     * <p>
     * The directive takes the following attributes:
     * <ul>
     *   <li>url: The url to upload the file to. Mandatory.</li>
     *   <li>multiple: Support single or multiple file upload. Defaults to false.</li>
     *   <li>auto-upload: Automatically start upload. Defaults to false.</li>
     *   <li>remove-after-upload: Remove file from queue once uploaded. Defaults to false.</li>
     *   <li>success(result): Success callback function. Optional.</li>
     *   <li>error(status, statusText): Error callback function. Optional.</li>
     * </ul>
     */
    .directive('fileUpload', ['FileUploader', 'Auth', function (FileUploader, Auth) {
        'use strict';

        return {
            restrict: 'AE',

            transclude: true,

            templateUrl: '/app/common/file-upload.html',

            scope: {
                url:                '=',
                multiple:           '=',
                dropText:           '@',
                fileTypes:          '=',
                autoUpload:         '=',
                removeAfterUpload:  '=',
                data:               '=',
                success:            '&',
                error:              '&'
            },

            compile: function(element, attrs) {

                if (attrs.dropText == undefined) {
                    attrs.$set("dropText", (attrs.multiple) ? 'or drop files here' : 'or drop file here');
                }

                // Return link function
                return {
                    pre: function (scope, element, attrs) {
                        // create a uploader with options
                        scope.uploader = new FileUploader({
                            scope: scope,
                            url: scope.url,
                            data: { uploadData: scope.data },
                            filters: []
                        });
                    },

                    post: function (scope) {

                        scope.extension = function (txt) {
                            return txt.substr((~-txt.lastIndexOf(".") >>> 0) + 2);
                        };

                        if (scope.data) {
                            scope.uploader.onBeforeUploadItem = function (item) {
                                item.formData.push({ data: JSON.stringify(scope.data) });
                            };
                        }

                        // Check if file-types are defined
                        if (scope.fileTypes) {
                            scope.uploader.filters.push({
                                name: 'filterName',
                                fn: function (item, options) {
                                    var ext = scope.extension(item.name).toLowerCase();
                                    return (ext && $.inArray(ext, scope.fileTypes.toLowerCase().split(",")) > -1);
                                }});
                        }

                        // Auto-upload
                        if (scope.autoUpload) {
                            scope.uploader.autoUpload = scope.autoUpload;
                        }

                        // Remove after upload
                        if (scope.removeAfterUpload) {
                            scope.uploader.removeAfterUpload = scope.removeAfterUpload;
                        }

                        // Handle authenticaiton
                        if (Auth.loggedIn) {
                            scope.uploader.headers.Authorization = 'bearer ' + Auth.keycloak.token;
                        }

                        scope.cancelOrRemove = function (item) {
                            if (item.isUploading) {
                                item.cancel();
                            } else {
                                item.remove();
                            }
                        };

                        scope.$watch(function () {
                            return scope.url;
                        }, function (value) {
                            scope.uploader.url = value;
                        }, true);

                        // Success call-back
                        if (scope.success) {
                            scope.uploader.onSuccessItem = function (item, response, status, headers) {
                                scope.success({ result: response});
                            };
                        }

                        // Error call-back
                        if (scope.error) {
                            scope.uploader.onErrorItem = function (item, response, status, headers) {
                                scope.error({ status: status, statusText: response.statusText });
                            };
                        }
                    }
                }
            }

        }
    }])


    /** Formats a data using moment() **/
    .filter('formatDate', [function () {
        return function(input, format) {
            format = format || 'lll';
            return input ? moment(input).format(format) : '';
        };
    }])


    /** Formats a number using numeral() **/
    .filter('numeral', [function () {
        return function(input, format) {
            format = format || '0,0';
            return input ? numeral(input).format(format) : '';
        };
    }]);








