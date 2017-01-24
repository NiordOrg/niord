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
 * Message editor directives.
 */
angular.module('niord.editor')

    /**********************************************
     * Directives that wraps an editable message
     * field with a title.
     **********************************************/
    .directive('editorField', ['$document', '$rootScope', function ($document, $rootScope) {
        return {
            restrict: 'E',
            templateUrl: '/app/editor/editor-field.html',
            replace: true,
            transclude: true,
            scope: {
                editMode:   "=",
                index:      "=",
                fieldId:    "@",
                fieldTitle: "@",
                fieldValid: '&',
                tabIndex:   '='
            },

            controller: function($scope) {

                $scope.isEditor = $rootScope.hasRole('editor');

                /** Returns if the field is currently editable **/
                this.fieldEditable = function () {
                    return $scope.isEditor && $scope.editMode[$scope.fieldId][$scope.index];
                }
            },

            link: function(scope, element, attrs) {

                scope.index = scope.index || 0;
                scope.tabIndex = scope.tabIndex || -1;
                scope.isEditor = $rootScope.hasRole('editor');

                var elm = $(element[0]);

                // Hook up a key listener that can be used to expand or collapse the field
                function keydownListener(evt) {
                    var editorLabel = elm.find('.editor-field-label');
                    var focused = editorLabel.is(':focus');
                    if (!focused || evt.isDefaultPrevented()) {
                        return evt;
                    }
                    if (evt.which == 39 /* right arrow */
                        || (!scope.editModeOn() && evt.which == 32 /* space */)
                        || (!scope.editModeOn() && evt.which == 13 /* enter */)) {
                        scope.updateEditField(evt, true);
                    } else if (evt.which == 37 /* left arrow */
                        || (scope.editModeOn() && evt.which == 32 /* space */)
                        || (scope.editModeOn() && evt.which == 13 /* enter */)) {
                        scope.updateEditField(evt, false);
                    } else if (evt.which == 38 /* up arrow */) {
                        var prev = scope.closestLabel(editorLabel.attr('tabindex'), true);
                        if (prev) {
                            prev.focus();
                            evt.preventDefault();
                        }
                    } else if (evt.which == 40 /* down arrow */) {
                        var next = scope.closestLabel(editorLabel.attr('tabindex'), false);
                        if (next) {
                            next.focus();
                            evt.preventDefault();
                        }
                    }
                }
                element.on('$destroy', function() {
                    $document.off('keydown', keydownListener);
                });
                $(element[0]).focusin(function() {
                    $document.on('keydown', keydownListener);
                });
                $(element[0]).focusout(function() {
                    $document.off('keydown', keydownListener);
                });


                /** The editor-fields are not siblings (because of message parts), so iterate over all fields **/
                scope.closestLabel = function (tabindex, prev) {
                    var index = -1;
                    var editorLabels = $('.editor-field-label');
                    editorLabels.each(function (i) {
                        if ($(this).attr('tabindex') == tabindex) {
                            index = i;
                        }
                    });
                    if (prev && index > 0) {
                        return editorLabels[index - 1];
                    } else if (!prev && index < editorLabels.length  - 1) {
                        return editorLabels[index + 1];
                    }
                    return null;
                };

                /** Toggles the edit mode of the field **/
                scope.toggleEditField = function () {
                    if (scope.isEditor) {
                        scope.editMode[scope.fieldId][scope.index] = !scope.editMode[scope.fieldId][scope.index];
                    }
                };


                /** Updates the edit state of the field editor from a keyboard event **/
                scope.updateEditField = function(evt, editModeOn) {
                    if (scope.isEditor) {
                        scope.editMode[scope.fieldId][scope.index] = editModeOn;
                        evt.preventDefault();
                        evt.stopPropagation();
                        scope.$$phase || scope.$apply();
                    }
                };


                /** Returns if the edit mode is on **/
                scope.editModeOn = function () {
                    return scope.isEditor && scope.editMode[scope.fieldId][scope.index];
                };


                /** Returns if the field value is invalid */
                scope.fieldInvalid = function () {
                    if (attrs.fieldValid) {
                        return !scope.fieldValid({ fieldId: scope.fieldId });
                    } else {
                        return false;
                    }
                };
            }
        };
    }])


    /** editorField sub-directive that displays the field value when the field is NOT being edited **/
    .directive('editorFieldValueViewer', [function () {
        return {
            restrict: 'E',
            template: '<div ng-if="showViewer()" ng-transclude></div>',
            replace: false,
            transclude: true,
            scope: {
            },
            require: '^editorField',
            link: function(scope, element, attrs, ctrl) {

                /** Returns whether to show the viewer or not **/
                scope.showViewer = function () {
                    return !ctrl.fieldEditable();
                }
            }
        };
    }])


    /** editorField sub-directive that displays the field value when the field is being edited **/
    .directive('editorFieldValueEditor', [function () {
        return {
            restrict: 'E',
            template:
                    '<div ng-if="showEditor()" class="container-fluid">' +
                    '  <div class="row"><div class="{{valueClass}}" ng-transclude></div></div>' +
                    '</div>',
            replace: false,
            transclude: true,
            scope: {
                valueClass: "@"
            },
            require: '^editorField',
            link: function(scope, element, attrs, ctrl) {

                /** Returns whether to show the editor or not **/
                scope.showEditor = function () {
                    return ctrl.fieldEditable();
                }
            }
        };
    }])


    /********************************
     * Renders the JSON diff structure
     ********************************/
    .directive('historyJsonDiff', [ '$document', function ($document) {
        'use strict';

        return {
            restrict: 'A',
            scope: {
                historyJsonDiff: "="
            },
            link: function(scope, element) {

                $document.on('click', function (e) {
                    e = e || window.event;
                    if (e.target.nodeName.toUpperCase() === "UL") {
                        if (e.target.getAttribute("closed") === "yes") {
                            e.target.setAttribute("closed", "no");
                        } else {
                            e.target.setAttribute("closed", "yes");
                        }
                    }
                });

                scope.$watchCollection(function () {
                        return scope.historyJsonDiff;
                    },
                    function (newValue) {
                        element.empty();

                        if (scope.historyJsonDiff.length > 0) {

                            try {
                                var hist1 = JSON.parse(scope.historyJsonDiff[0].snapshot);
                                var hist2 = (scope.historyJsonDiff.length > 1)
                                    ? JSON.parse(scope.historyJsonDiff[1].snapshot)
                                    : hist1;
                                jsond.compare(hist1, hist2, "Message", element[0]);
                            } catch (e) {
                                console.error("Error " + e);
                            }
                        }

                    });
            }
        };
    }]);

