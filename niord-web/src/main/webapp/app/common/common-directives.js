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
                readonly: '=',
                placeholder: '@'
            },
            template : '<div class="input-group date" data-date-format="l">'
                     + '  <input type="text" class="input-sm form-control" />'
                     + '  <span class="input-group-addon">'
                     + '    <span class="glyphicon glyphicon-calendar"></span>'
                     + '  </span>'
                     + '</div>',

            link : function(scope, element, attrs, ctrl) {

                var locale = $rootScope.language;

                var input = element.find("input");

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


                element.bind('dp.change dp.hide', function(ev) {
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

                        // Handle domain and authentication headers (todo: move to common function)
                        if ($rootScope.domain) {
                            scope.uploader.headers.NiordDomain = $rootScope.domain.clientId;
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







