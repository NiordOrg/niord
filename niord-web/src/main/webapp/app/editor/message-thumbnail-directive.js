/**
 * Message thumbnail directives.
 */
angular.module('niord.editor')

/****************************************************************
 * Replaces the content of the element with the area description
 ****************************************************************/
    .directive('messageThumbnail', ['$rootScope', '$document',
        function ($rootScope, $document) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: '/app/editor/message-thumbnail-directive.html',
            scope: {
                message: "="
            },
            link: function(scope, element, attrs) {

                scope.wmsLayerEnabled = $rootScope.wmsLayerEnabled;
                scope.mode = undefined;
                scope.takeThumbnail = false;

                /** ************************ **/
                /** Editor actions           **/
                /** ************************ **/


                /** Opens the snapshot editor */
                scope.openEditor = function () {
                    scope.mode = 'editor';
                };


                /** Exits the snapshot editor */
                scope.exitEditor = function () {
                    scope.mode = undefined;
                };


                /** Takes a thumbnail */
                scope.thumbnail = function () {
                    scope.takeThumbnail = true;
                };


                /** ************************ **/
                /** Event handling           **/
                /** ************************ **/


                // Hook up a key listener that closes the editor when Escape is pressed
                function keydownListener(evt) {
                    if (evt.isDefaultPrevented()) {
                        return evt;
                    }
                    if (scope.mode == 'editor' && evt.which == 27) {
                        scope.exitEditor();
                        evt.preventDefault();
                        evt.stopPropagation();
                        scope.$$phase || scope.$apply();
                    }
                }

                $document.on('keydown', keydownListener);

                element.on('$destroy', function() {
                    $document.off('keydown', keydownListener);
                });

            }
        };
    }])


    /**
     * The actual message thumbnail map layer
     */
    .directive('messageThumbnailLayer', ['MapService', function (MapService) {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
                takeThumbnail: '='
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;

                olScope.getMap().then(function(map) {

                    /** Takes a thumbnail */
                    scope.thumbnail = function () {
                        if (scope.takeThumbnail) {
                            scope.takeThumbnail = false;
                            
                            var exportPNGElement = document.getElementById('snapshot');
                            map.once('postcompose', function(event) {
                                var canvas = event.context.canvas;
                                exportPNGElement.href = canvas.toDataURL('image/png');
                            });
                            map.renderSync();
                        }
                    };

                    scope.$watch("takeThumbnail", scope.thumbnail, true);


                });

            }
        };
    }]);



