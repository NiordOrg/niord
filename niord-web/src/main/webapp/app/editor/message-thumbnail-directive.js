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
 * Message thumbnail directives.
 */
angular.module('niord.editor')


    /**
     * The actual message thumbnail map layer
     */
    .directive('messageThumbnailLayer', [function () {
        return {
            restrict: 'E',
            replace: false,
            require: '^olMap',
            scope: {
                thumbnailGenerated: '&'
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();

                olScope.getMap().then(function(map) {

                    /** Takes a thumbnail. Event emitted by parent directive */
                    scope.$on('take-thumbnail', function() {
                        map.once('postcompose', function(event) {
                            var canvas = event.context.canvas;

                            // The canvas size depends on the pixel ratio. Scale down to 1:1.
                            // See http://stackoverflow.com/questions/35694880/openlayers-3-export-map-to-png-image-size
                            var image;
                            if (ol.has.DEVICE_PIXEL_RATIO == 1) {
                                image = canvas.toDataURL('image/png');
                            } else {
                                var targetCanvas = document.createElement('canvas');
                                var size = map.getSize();
                                targetCanvas.width = size[0];
                                targetCanvas.height = size[1];
                                targetCanvas.getContext('2d').drawImage(canvas,
                                    0, 0, canvas.width, canvas.height,
                                    0, 0, targetCanvas.width, targetCanvas.height);
                                image = targetCanvas.toDataURL('image/png');
                            }

                            // Notify via call-back
                            if (attrs.thumbnailGenerated) {
                                scope.thumbnailGenerated({image: image});
                            }
                        });
                        map.renderSync();
                    })
                });
            }
        };
    }]);



