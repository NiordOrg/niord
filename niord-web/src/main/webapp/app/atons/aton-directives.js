/**
 * Assorted Aid-to-Navigation directives.
 */
angular.module('niord.atons')

    /**
     * Renders the AtoN details
     */
    .directive('atonListDetails', ['$rootScope', 'MapService', 'AtonService',
        function ($rootScope, MapService, AtonService) {
            return {
                restrict: 'E',
                replace: false,
                templateUrl: '/app/atons/aton-list-details.html',
                scope: {
                    aton: '=',
                    selection: '=',
                    zoomBtn: '@',
                    editable: '@'
                },
                link: function(scope) {

                    scope.showZoomBtn = scope.zoomBtn == 'true';
                    scope.showDndBtn = scope.editable == 'true';
                    scope.showEditBtn = scope.editable == 'true';

                    // Returns if the given AtoN is selected or not
                    scope.isSelected = function (aton) {
                        return scope.selection.get(AtonService.getAtonUid(aton)) !== undefined;
                    };

                    // Toggle the selection state of the AtoN
                    scope.toggleSelectAton = function (aton) {
                        if (scope.isSelected(aton)) {
                            scope.unselectAton(aton);
                        } else {
                            scope.selectAton(aton);
                        }
                    };

                    // Selects the given AtoN
                    scope.selectAton = function (aton) {
                        if (!scope.isSelected(aton)) {
                            // NB: We add a copy that can be modified on the selection page
                            scope.selection.put(AtonService.getAtonUid(aton), {
                                aton: angular.copy(aton),
                                orig: aton
                            });
                        }
                    };

                    // De-selects the given AtoN
                    scope.unselectAton = function (aton) {
                        if (scope.isSelected(aton)) {
                            scope.selection.remove(AtonService.getAtonUid(aton));
                        }
                    };

                    // Zooms in on the map
                    scope.zoomToAton = function (aton) {
                        $rootScope.$broadcast('zoom-to-aton', aton);
                    };

                    // Edits the attributes of the AtoN
                    scope.editAton = function (aton) {
                        var atonCtx = scope.selection.get(AtonService.getAtonUid(scope.aton));
                        if (atonCtx != null) {
                            $rootScope.$broadcast('edit-aton-details', atonCtx);
                        }
                    };

                    // Returns if the given attribute is changed compared with the original
                    scope.changed = function (attr) {
                        var atonCtx = scope.selection.get(AtonService.getAtonUid(scope.aton));
                        if (attr) {
                            return atonCtx != null && !angular.equals(atonCtx.orig[attr], atonCtx.aton[attr]);
                        }
                        // If no attribute is specified, check if there are any changes
                        return atonCtx != null && !angular.equals(atonCtx.orig, atonCtx.aton);
                    };

                    // Returns if the icon has changed
                    scope.iconChanged = function () {
                        var atonCtx = scope.selection.get(AtonService.getAtonUid(scope.aton));
                        return atonCtx != null && atonCtx.orig.iconUrl != atonCtx.aton.iconUrl;
                    };

                    // Returns if the position has changed
                    scope.posChanged = function () {
                        var atonCtx = scope.selection.get(AtonService.getAtonUid(scope.aton));
                        return atonCtx != null && (
                            (atonCtx.orig.lat != scope.aton.lat) ||
                            (atonCtx.orig.lon != scope.aton.lon));
                    };

                }
            };
        }])


    /**
     * Tag used for showing and editing AtoN OSM tags in-place.
     *
     * The directive can be used for editing both the key and the value of the tag.
     * If the value is edited, specify the "for-key" attribute.
     */
    .directive( 'editTagInPlace', [ '$http', '$document', function($http, $document) {
        return {
            restrict: 'E',
            scope: {
                aton:   '=',
                forKey: '=',
                value:  '='
            },
            template: '<div ng-click="edit()" class="edit-tag-label">{{value || "&nbsp;"}}</div>' +
                      '<div class="edit-tag-editor">' +
                      '  <input xxx-ng-blur="endEdit(false)" ng-model="editValue" autocomplete="off" spellcheck="false" ' +
                      '        uib-typeahead="val for val in autoComplete($viewValue)" typeahead-wait-ms="300" '+
                      '        typeahead-focus-first="falses" typeahead-focus-on-select="false" />' +
                      '  <span class="edit-tag-btn glyphicon glyphicon-ok" ng-click="endEdit(true)"></span>' +
                      '  <span class="edit-tag-btn glyphicon glyphicon-remove" ng-click="endEdit(false)"></span>' +
                      '</div>',
            link: function (scope, element) {

                var spanElement = angular.element( element.children()[0] );
                var editorElement = angular.element( element.children()[1] );

                scope.editValue = angular.copy(scope.value);
                
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


                /** Creates an auto-complete list */
                scope.autoComplete = function (viewValue) {
                    var url = '/rest/atons/';
                    if (scope.forKey) {
                        // We are editing a tag value
                        url += 'auto-complete-value?key=' + encodeURIComponent(scope.forKey)
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


                /** Called to enable editing of the value */
                scope.edit = function () {
                    $document.on('keydown', keydownListener);
                    spanElement.css('display', 'none');
                    editorElement.css('display', 'inline-block');
                    editorElement.find('input')[0].focus();
                };


                /** Called to end editing of the value */
                scope.endEdit = function (save) {
                    if (save) {
                        scope.value = angular.copy(scope.editValue);
                    } else {
                        scope.editValue = angular.copy(scope.value);
                    }
                    $document.off('keydown', keydownListener);
                    spanElement.css('display', 'inline-block');
                    editorElement.css('display', 'none');
                };
            }
        };
    }]);

