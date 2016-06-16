/**
 * Message editor directives.
 */
angular.module('niord.editor')

    /**********************************************
     * Directives that wraps an editable message
     * field with a title.
     **********************************************/
    .directive('editorField', [function () {
        return {
            restrict: 'E',
            templateUrl: '/app/editor/editor-field.html',
            replace: true,
            transclude: true,
            scope: {
                editMode:   "=",
                fieldId:    "@",
                fieldTitle: "@",
                fieldValid: '&'
            },

            controller: function($scope) {

                /** Returns if the field is currently editable **/
                this.fieldEditable = function () {
                    return $scope.editMode[$scope.fieldId];
                }
            },

            link: function(scope, element, attrs) {

                /** Toggles the edit mode of the field **/
                scope.toggleEditField = function () {
                    scope.editMode[scope.fieldId] = !scope.editMode[scope.fieldId];
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
    }])


    /********************************
     * Defines a message id field, e.g
     * used for references.
     ********************************/
    .directive('messageIdField', [ '$http', '$rootScope', function ($http, $rootScope) {
        'use strict';

        return {
            restrict: 'E',
            templateUrl: '/app/editor/message-id-field.html',
            replace: false,
            scope: {
                reference:  "=",
                minLength:  "="
            },
            link: function(scope) {

                scope.minLength = scope.minLength | 3;

                // Use for message id selection
                scope.messageIds = [];
                scope.refreshMessageIds = function (text) {
                    if (!text || text.length < scope.minLength) {
                        return [];
                    }
                    return $http.get(
                        '/rest/messages/search-message-ids?txt=' + encodeURIComponent(text) +
                        '&lang=' + $rootScope.language
                    ).then(function(response) {
                        scope.messageIds = response.data;
                    });
                };

            }
        }
    }]);



