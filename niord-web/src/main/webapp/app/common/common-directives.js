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
 * Common directives.
 */
angular.module('niord.common')

    /****************************************************************
     * The areas-field directive supports selecting either a
     * single area or a list of areas. For single-tag selection use
     * areaData.area and for multi-area selection use areaData.areas.
     * Use "init-ids" to initialize the areas using a list of area ids.
     ****************************************************************/
    .directive('areasField', ['$rootScope', '$http', 'LangService', function($rootScope, $http, LangService) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/common/areas-field.html',
            scope: {
                areaData:       "=",
                initIds:        "=",
                domain:         "=",
                messageSorting: "=",
                multiple:       "="
            },
            link: function(scope) {

                scope.formatParents = LangService.formatParents;
                scope.areaData = scope.areaData || {};
                scope.multiple = scope.multiple || false;
                scope.domain = scope.domain || false;
                scope.messageSorting = scope.messageSorting || false;

                if (scope.multiple && !scope.areaData.areas) {
                    scope.areaData.areas = [];
                }


                // init-ids can be used to instantiate the field from a list of area IDs
                scope.$watch("initIds", function (initIds) {
                    if (initIds && initIds.length > 0) {
                        $http.get('/rest/areas/search/' + initIds.join() + '?lang=' + $rootScope.language + '&limit=20')
                            .then(function(response) {
                                angular.forEach(response.data, function (area) {
                                    if (scope.multiple) {
                                        scope.areaData.areas.push(area);
                                    } else {
                                        scope.areaData.area = area;
                                    }
                                });
                            });
                    }
                }, true);


                /** Refreshes the areas search result */
                scope.searchResult = [];
                scope.refreshAreas = function(name) {
                    if (!name || name.length == 0) {
                        return [];
                    }
                    return $http.get(
                        '/rest/areas/search?name=' + encodeURIComponent(name) +
                        '&domain=' + scope.domain +
                        '&messageSorting=' + scope.messageSorting +
                        '&lang=' + $rootScope.language +
                        '&limit=10'
                    ).then(function(response) {
                        scope.searchResult = response.data;
                    });
                };


                /** Removes the current area selection */
                scope.removeAreas = function () {
                    if (scope.multiple) {
                        scope.areaData.areas.length = 0;
                    } else {
                        scope.areaData.area = undefined;
                    }
                };
            }
        }
    }])



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
     * Used for editing a lat-lon position in an input field using a mask.
     * <p>
     * TODO: Proper handling of positions outside the normal lat-lon ranges (e.g. 512°E)
     *       - Roll over to a valid position range.
     */
    .directive('positionInput', ['$timeout', function($timeout) {
        return {
            restrict: 'E',
            templateUrl:  '/app/common/position-input.html',
            replace: true,
            scope: {
                lat:            "=",
                lon:            "=",
                decimals:       "=",
                posClass:       "=",
                posRequired:    "=",
                posChange:      "&",
                placeholder:    "@"
            },
            compile: function() {

                // Note to self: The "compile" function is used rather than the "link" because the "uib-mask"
                // directive will not pick up "uib-options", unless they are defined in the "pre" function below.

                return {
                    pre: function (scope, element, attrs) {

                        scope.decimals = attrs.decimals ? scope.decimals : 3;
                        scope.placeholder = scope.placeholder || 'Latitude - Longitude';
                        scope.latlon = undefined;
                        scope.posClass = scope.posClass || {};

                        var decimalMask = '99999999'.substr(0, scope.decimals);
                        if (scope.decimals > 0) {
                            var decimalDelim = numeral(0.0).format('.0').substr(0, 1);
                            decimalMask = decimalDelim + decimalMask;
                        }

                        scope.latMask = '99° 59' + decimalMask + '\'Y';
                        scope.lonMask = '199° 59' + decimalMask + '\'X';
                        scope.mask = scope.latMask + "  -  " + scope.lonMask;
                        scope.options = {
                            maskDefinitions: { 'X': /[ewEW]/, 'Y': /[nsNS]/, '1': /[01]/, '5': /[0-5]/ }
                        };
                    },

                    post: function (scope, element, attrs) {

                        // Get a reference to the input field
                        var inputField = $(element[0]).find('input');

                        // When a blank input field loses focus, display placeholder
                        inputField.bind('blur', function () {
                            if (scope.latlon === undefined) {
                                inputField.attr('placeholder', scope.placeholder);
                            }
                        });

                        // Remove any place-holder from the input field when it is focused
                        inputField.bind('focus', function () {
                            inputField.removeAttr('placeholder');
                        });

                        // Initially, display the placeholder
                        $timeout(function() { inputField.attr('placeholder', scope.placeholder); });

                        /** Generic function that prepends a character to a string **/
                        function pad(n, width, z) {
                            z = z || '0';
                            n = n + '';
                            return n.length >= width ? n : new Array(width - n.length + 1).join(z) + n;
                        }


                        /**
                         * Parses the string value to a latitude or longitude
                         * <p>
                         * If the mask is e.g. "99° 59,999\'Y  -  199° 59,999\'X", then a valid position
                         * for the pos 56 30.444'N 11 05.111'E will have the format: "5630444N01105111E"
                         **/
                        function parse(val, lat) {
                            var degLen = lat ? 2 : 3;
                            if (val == undefined || val.length != degLen + 2 + scope.decimals + 1) {
                                return undefined;
                            }
                            val = val.toUpperCase();
                            var degreeStr = val.substr(0, degLen);
                            var minuteStr = val.substr(degLen, 2 + scope.decimals);
                            var direction = val.substr(val.length - 1);
                            var sign = direction == 'N' || direction == 'E' ? 1 : -1;

                            return sign * (
                                    parseInt(degreeStr)
                                    + parseFloat(minuteStr.substr(0, 2) + '.' + minuteStr.substr(2)) / 60.0
                                );
                        }


                        /**
                         * Formats the decimal latitude or longitude as a string
                         * <p>
                         * If the mask is e.g. "99° 59,999\'Y  -  199° 59,999\'X", then a valid position
                         * for the pos 56 30.444'N 11 05.111'E will have the format: "5630444N01105111E"
                         */
                        function format(val, lat) {
                            if (val === undefined) {
                                return undefined;
                            }
                            var dir = lat ? (val < 0 ? 'S' : 'N') : (val < 0 ? 'W' : 'E');
                            val = Math.abs(val);
                            var degrees = Math.floor(val);
                            var minutes = (val - degrees) * 60;
                            var decimalStr = [ '0', '0.0', '0.00', '0.000', '0.0000', '0.00000'];

                            return pad(degrees, lat ? 2 : 3)
                                 + pad(Math.floor(minutes), 2)
                                 + numeral(minutes - Math.floor(minutes)).format(decimalStr[scope.decimals]).substr(2)
                                 + dir;
                        }


                        // Watch for changes to the underlying position model
                        scope.$watch("[lat,lon]", function (value) {
                            var latSpec = format(scope.lat, true);
                            var lonSpec = format(scope.lon, false);
                            scope.latlon = latSpec !== undefined && lonSpec !== undefined ? latSpec + lonSpec : undefined;
                        }, true);


                        // Watch for changes to the input field value
                        scope.$watch("latlon", function (latlon, oldLatlon) {
                            if (latlon == oldLatlon) {
                                return;
                            }
                            var latSpec = undefined;
                            var lonSpec = undefined;
                            if (latlon && latlon.length > 0) {
                                // NB: lon-spec is one char longer than lat-spec
                                var index = Math.floor(latlon.length / 2);
                                latSpec = latlon.substr(0, index);
                                lonSpec = latlon.substr(index);
                            }
                            scope.lat = parse(latSpec, true);
                            scope.lon = parse(lonSpec, false);


                            // Broadcast change
                            if (attrs.posChange) {
                                scope.posChange({});
                            }
                        }, true);


                        /** Called in order to clear the position input field **/
                        scope.clearPos = function () {
                            scope.latlon = undefined;
                            $timeout(function() { inputField[0].focus() } );
                        };
                    }
                }
            }
        }
    }])


    /**
     * Tag an input field with this directive to avoid changes making the form dirty. See:
     * http://stackoverflow.com/questions/28721959/angular-1-2-is-it-possible-to-exclude-an-input-on-form-dirty-checking/28722106
     */
    .directive('ignoreDirty', [function() {
        return {
            restrict: 'A',
            require: 'ngModel',
            scope: {
                ignoreDirty: "="
            },
            link: function(scope, elm, attrs, ctrl) {
                if (scope.ignoreDirty === true) {
                    ctrl.$setPristine = function() {};
                    ctrl.$pristine = false;
                }
            }
        }
    }])


    .directive('jsonData', [function () {
        return {
            restrict: 'E',
            scope: {
                data: "=",
                json: "="
            },
            link: function(scope) {
                try {
                    scope.data = JSON.parse(scope.json);
                } catch (e) {}
                scope.$watch(
                    function() { return scope.json; },
                    function () {
                        try {
                            scope.data = JSON.parse(scope.json);
                        } catch (e) {}
                    },
                    true);
                scope.$watch(
                    function() { return scope.data; },
                    function () {
                        try {
                            scope.json = JSON.stringify(scope.data);
                        } catch (e) {}
                    },
                    true);
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
                format: '=',
                time: '=',
                readonly: '=',
                placeholder: '@',
                size: '@'
            },
            template : '<div class="input-group date" data-date-format="l">'
                     + '  <input type="text" class="input-{{size}} form-control" />'
                     + '  <span class="input-group-addon">'
                     + '    <span class="glyphicon glyphicon-calendar"></span>'
                     + '  </span>'
                     + '</div>',

            link : function(scope, element, attrs, ctrl) {

                var locale = $rootScope.language;

                var input = element.find("input");

                scope.size = scope.size || 'sm';
                scope.format = scope.format || "DD/MM/YYYY HH:mm";
                scope.$watch("format", function () {
                    element.attr('data-date-format', scope.format);
                    if (picker) {
                        picker.format(scope.format);
                    }
                }, true);

                if (scope.readonly) {
                    input.attr('readonly', "readonly");
                }

                if (scope.id) {
                    $(element).attr('id', scope.id);
                }

                if (scope.placeholder) {
                    input.attr('placeholder', scope.placeholder);
                }

                var picker = $(element).datetimepicker({
                    locale: locale,
                    useCurrent: true,
                    showTodayButton: true,
                    showClear: true
                }).data('DateTimePicker');


                ctrl.$formatters.push(function (modelValue) {
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


                ctrl.$parsers.push(function (viewValue) {
                    if (!picker.date()) {
                        return null;
                    }
                    return picker.date().valueOf();
                });


                /** If a time parameter has been specified, adjust the date accordingly **/
                function adjustTime() {
                    var date = picker.date();
                    var millis = null;
                    if (date) {
                        millis = date.valueOf();
                        if (scope.time && scope.time.length > 0) {
                            var hms = scope.time.split(":");
                            if (hms.length == 3) {
                                date.set({
                                    'hour': parseInt(hms[0]),
                                    'minute': parseInt(hms[1]),
                                    'second': parseInt(hms[2]),
                                    'millisecond': 0
                                });
                                if (millis != date.valueOf()) {
                                    picker.date(date);
                                    millis = date.valueOf();
                                }
                            }
                        }
                    }
                    return millis;
                }

                scope.$watch("time", adjustTime, true);

                element.bind('dp.change dp.hide', function(ev) {
                    var millis = adjustTime();

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
    .directive('fileUpload', ['$rootScope', 'FileUploader', 'AuthService',
        function ($rootScope, FileUploader, AuthService) {
        'use strict';

        return {
            restrict: 'AE',

            transclude: true,

            templateUrl: '/app/common/file-upload.html',

            scope: {
                url:                '=',
                multiple:           '=',
                dropText:           '@',
                uploadText:         '@',
                removeText:         '@',
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

                        scope.uploadText = scope.uploadText || "Upload all";
                        scope.removeText = scope.removeText || "Remove all";

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

                        // Handle domain and authentication headers (todo: move to common function)
                        if ($rootScope.domain) {
                            scope.uploader.headers.NiordDomain = $rootScope.domain.domainId;
                        }
                        if (AuthService.keycloak.token) {
                            scope.uploader.headers.Authorization = 'bearer ' + AuthService.keycloak.token;
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


    /**
     * Directive that wraps the fancytree jQuery plugin
     * Used for hierarchical entities such as areas and categories
     */
    .directive('entityTree', [ function () {
        'use strict';

        return {
            restrict: 'AE',
            scope: {
                entities:           '=',
                filter:             '=',
                flagInactive:       '=',
                sort :              '@',
                entitySelected :    '&',
                entityMoved :       '&'
            },

            link: function (scope, element, attrs) {

                scope.sort = (attrs.sort !== undefined) ? attrs.sort : false;

                // Initialize the tree
                element.fancytree({
                    source: [],
                    keyboard: true,
                    extensions: ["filter", "dnd"],
                    filter: {
                        mode: "hide"
                    },
                    dnd: {
                        autoExpandMS: 400,
                        draggable: {
                            zIndex: 1000,
                            scroll: false
                        },
                        preventVoidMoves: true,
                        preventRecursiveMoves: true,
                        dragStart: function() { return true; },
                        dragEnter: function(node, data) {
                            if (node.parent === data.otherNode.parent) {
                                return ['over'];
                            }
                            return true;
                        },
                        dragOver: function(node, data) {},
                        dragLeave: function(node, data) {},
                        dragStop: function(node, data) {},
                        dragDrop: function(node, data) {
                            handleDragDrop(node, data);
                        }
                    },
                    activate: function(event, data){
                        var node = data.node;
                        if (scope.entitySelected) {
                            scope.entitySelected({ entity: node.data.entity });
                        }
                    }
                });

                var tree = element.fancytree("getTree");

                /**
                 * Convert the list of entities into the tree structure used by
                 * https://github.com/mar10/fancytree/
                 */
                function toTreeData(entities, treeData, level) {
                    for (var i = 0; i < entities.length; i++) {
                        var entity = entities[i];
                        var title = (entity.descs && entity.descs.length > 0) ? entity.descs[0].name : 'N/A';
                        var node = { key: entity.id, title: title, folder: true, children: [], level: level, entity: entity };
                        if (scope.flagInactive && !entity.active) {
                            node.icon = '/img/inactive-folder.gif';
                        }
                        treeData.push(node);
                        if (entity.children && entity.children.length > 0) {
                            toTreeData(entity.children, node.children, level + 1);
                        }
                    }
                }

                /** Called when a dragged element has been dropped */
                function handleDragDrop(node, data) {
                    if (scope.entityMoved) {
                        var entity = data.otherNode.data.entity;
                        var parent = undefined;
                        if (data.hitMode == 'before' || data.hitMode == 'after') {
                            parent = (node.parent.data.entity) ? node.parent.data.entity : undefined;
                        } else if (data.hitMode == 'over') {
                            parent = node.data.entity;
                        }
                        scope.entityMoved({ entity: entity, parent: parent });

                    } else {
                        data.otherNode.moveTo(node, data.hitMode);
                    }
                }

                /** Watch entities **/
                scope.$watchCollection(function () {
                    return scope.entities;
                }, function (newValue) {
                    if (tree.options.source && tree.options.source.length > 0) {
                        scope.storeState();
                    }
                    var treeData = [];
                    if (newValue) {
                        toTreeData(newValue, treeData, 0);
                    }
                    tree.options.source = treeData;
                    tree.reload();
                    if (scope.sort) {
                        tree.rootNode.sortChildren(null, true);
                    }
                    tree.clearFilter();
                    scope.collapseAll();
                    scope.restoreState();
                    if (scope.filter) {
                        tree.filterNodes(scope.filter);
                    }
                });


                /** Watch the filter **/
                if (attrs.filter) {
                    scope.$watch(function () {
                        return scope.filter
                    }, function (newValue) {
                        var val = newValue || '';
                        if (val != '') {
                            tree.filterNodes(val);
                            scope.expandAll();
                        } else {
                            tree.clearFilter();
                            scope.collapseAll();
                        }
                    }, true);
                };


                /** Stores the current expanded state */
                scope.storeState = function() {
                    scope.expandedIds = [];
                    scope.activeKey = tree.getActiveNode() ? tree.getActiveNode().key : undefined;
                    tree.visit(function(node){
                        if (node.expanded) {
                            scope.expandedIds.push(node.key);
                        }
                    });
                };


                /** Restores the previously stored expanded state */
                scope.restoreState = function() {
                    if (scope.expandedIds) {
                        tree.visit(function(node){
                            node.setExpanded($.inArray(node.key, scope.expandedIds) > -1);
                        });
                    }
                    if (scope.activeKey) {
                        tree.activateKey(scope.activeKey);
                    }
                };


                /** Collapses all tree nodes except the root node */
                scope.collapseAll = function() {
                    tree.visit(function(node){
                        node.setExpanded(node.data.level == 0);
                    });

                };


                /** Expands all tree nodes */
                scope.expandAll = function() {
                    tree.visit(function(node){
                        node.setExpanded(true);
                    });
                };
            }
        };
    }]);







