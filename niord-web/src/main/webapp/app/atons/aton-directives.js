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
 * Assorted Aid-to-Navigation directives.
 */
angular.module('niord.atons')

    /**
     * Renders the AtoN details.
     */
    .directive('atonListDetails', ['$rootScope', 'MapService', 'AtonService',
        function ($rootScope, MapService, AtonService) {
            return {
                restrict: 'E',
                replace: false,
                templateUrl: '/app/atons/aton-list-details.html',
                scope: {
                    aton:       '=',
                    origAton:   '=',
                    selection:  '=',
                    zoomBtn:    '@',
                    editable:   '@',
                    draggable:  '@',
                    selectable: '@',
                    atonEdited: '&',
                    userData:   '='
                },
                link: function(scope, element, attrs) {

                    scope.showZoomBtn = scope.zoomBtn == 'true';
                    scope.showDndBtn = scope.draggable == 'true';
                    scope.showEditBtn = scope.editable == 'true';
                    scope.showSelectBtn = scope.selectable == 'true';

                    
                    // Returns the AtoN context associated with the current AtoN
                    scope.getAtonCtx = function() {
                        if  (scope.selection) {
                            return scope.selection.get(AtonService.getAtonUid(scope.aton));
                        } else {
                            return { aton: scope.aton, orig: scope.origAton };
                        }
                    };


                    // Returns if the given AtoN is selected or not
                    scope.isSelected = function () {
                        return scope.getAtonCtx() !== undefined;
                    };

                    // Toggle the selection state of the AtoN
                    scope.toggleSelectAton = function () {
                        if (scope.isSelected()) {
                            scope.unselectAton();
                        } else {
                            scope.selectAton();
                        }
                    };

                    // Selects the given AtoN
                    scope.selectAton = function () {
                        if (scope.selection && !scope.isSelected()) {
                            // NB: We add a copy that can be modified on the selection page
                            scope.selection.put(AtonService.getAtonUid(scope.aton), {
                                aton: angular.copy(scope.aton),
                                orig: scope.aton
                            });
                        }
                    };

                    // De-selects the given AtoN
                    scope.unselectAton = function () {
                        if (scope.selection && scope.isSelected()) {
                            scope.selection.remove(AtonService.getAtonUid(scope.aton));
                        }
                    };

                    // Zooms in on the map
                    scope.zoomToAton = function () {
                        $rootScope.$broadcast('zoom-to-aton', scope.aton);
                    };

                    // Edits the attributes of the AtoN
                    scope.editAton = function () {
                        var atonCtx = scope.getAtonCtx();
                        if (atonCtx != null) {
                            var orig = atonCtx.orig ? atonCtx.orig : angular.copy(scope.aton);
                            AtonService.atonEditorDialog(atonCtx.aton, orig).result
                                .then(function (aton) {
                                    if (attrs.atonEdited) {
                                        scope.atonEdited({aton: aton, userData: scope.userData });
                                    }
                                });
                        }
                    };

                    // Views the attributes of the AtoN
                    scope.viewAton = function () {
                        AtonService.atonDetailsDialog(scope.aton, true)
                            .result.then(scope.selectAton);
                    };


                    // Returns if the given attribute is changed compared with the original
                    scope.changed = function (attr) {
                        var atonCtx = scope.getAtonCtx();
                        if (attr) {
                            return atonCtx != null && atonCtx.orig != null && !angular.equals(atonCtx.orig[attr], atonCtx.aton[attr]);
                        }
                        // If no attribute is specified, check if there are any changes
                        return atonCtx != null && atonCtx.orig != null && !angular.equals(atonCtx.orig, atonCtx.aton);
                    };

                    // Returns if the icon has changed
                    scope.iconChanged = function () {
                        var atonCtx = scope.getAtonCtx();
                        return atonCtx != null && atonCtx.orig != null && atonCtx.orig.iconUrl != atonCtx.aton.iconUrl;
                    };

                    // Returns if the position has changed
                    scope.posChanged = function () {
                        var atonCtx = scope.getAtonCtx();
                        return atonCtx != null && atonCtx.orig != null && (
                            (atonCtx.orig.lat != scope.aton.lat) ||
                            (atonCtx.orig.lon != scope.aton.lon));
                    };

                }
            };
        }])


    /**
     * Tag used for showing and editing AtoN OSM tags.
     * If may work in "in-place" or "standard" mode.,
     *
     * The directive can be used for editing both the key and the value of the tag.
     * If the value is edited, specify the "for-key" attribute.
     */
    .directive( 'atonTagEditor', [ '$http', '$document', '$timeout', function($http, $document, $timeout) {
        return {
            restrict: 'E',
            templateUrl: '/app/atons/aton-tag-editor.html',
            scope: {
                aton:   '=',
                mode:   '@',
                tag:    '=',
                attr:   '@',
                placeholder: '@'
            },

            link: function (scope, element) {

                scope.mode = scope.mode || 'in-place';
                scope.attr = scope.attr == 'k' ? 'k' : 'v';
                scope.value = { val: scope.tag[scope.attr] }; // NB: Nested because of scope madness!
                scope.editing = scope.mode != 'in-place';

                /** "in-place" editor mode **/
                if (scope.mode == 'in-place') {

                    // Hook up a key listener that closes the editor when Escape, Tab and Enter is pressed
                    function keydownListener(evt) {
                        if (evt.isDefaultPrevented()) {
                            return evt;
                        }
                        if (evt.which == 27 || evt.which == 13 || evt.which == 9) {
                            scope.endEdit(evt.which == 13 || evt.which == 9);
                            evt.preventDefault();
                            evt.stopPropagation();
                            scope.$$phase || scope.$apply();
                        }
                    }
                    element.on('$destroy', function() {
                        $document.off('keydown', keydownListener);
                    });

                    /** Called to enable editing of the value */
                    scope.edit = function () {
                        scope.value.val = scope.tag[scope.attr];
                        scope.editing = true;
                        $document.on('keydown', keydownListener);
                        $timeout(function () { $(element[0]).find('input')[0].focus() });
                    };


                    /** Called to end editing of the value */
                    scope.endEdit = function (save) {
                        if (save) {
                            scope.tag[scope.attr] = scope.value.val;
                        } else {
                            scope.value.val = scope.tag[scope.attr];
                        }
                        scope.editing = false;
                        $document.off('keydown', keydownListener);
                    };

                } else {
                    /** "standard" editor mode **/

                    // Sync scope.value and tag[attr] two-ways
                    scope.$watch(
                        "value",
                        function () { scope.tag[scope.attr] = scope.value.val; },
                        true);
                    scope.$watch(
                        function () { return scope.tag[scope.attr]; },
                        function () {  scope.value.val = scope.tag[scope.attr]; },
                        true);
                }


                /** Creates an auto-complete list */
                scope.autoComplete = function (viewValue) {
                    var url = '/rest/atons/defaults/';
                    if (scope.attr == 'v') {
                        // We are editing a tag value
                        url += 'auto-complete-value?key=' + encodeURIComponent(scope.tag.k)
                            + '&value=' + encodeURIComponent(viewValue);
                    } else {
                        // We are editing a tag key
                        url += 'auto-complete-key?key=' + encodeURIComponent(viewValue);
                    }
                    return $http.post(url, scope.aton)
                        .then(function(response) {
                            return response.data;
                        });
                };
            }
        };
    }]);

