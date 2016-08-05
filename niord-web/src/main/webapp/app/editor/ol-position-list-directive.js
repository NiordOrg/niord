/**
 * The position list directive used for simplified feature geometry editing
 * <p>
 * The editor can be initialized with "edit-type" set to the following values:
 * <ul>
 *     <li>"features": You edit a GeoJson feature collection.</li>
 *     <li>"feature": You edit a GeoJson feature collection, but when you save, all features are merged
 *                   into a single feature.</li>
 *     <li>"message": You edit the feature collection of a message, which means that there are a lot of extra
 *                    functionality, such as allowing the user to specify names for each feature and position,
 *                    an affected radius, etc.</li>
 * </ul>
 * <p>
 * A few things to keep in mind:
 * <ul>
 *     <li>For the exterior and interior linear rings of polygons, the last position is not displayed
 *         since it is the same as the first position.</li>
 * </ul>
 */
angular.module('niord.editor')

    .directive('olPositionList', ['$rootScope', '$timeout', 'MapService',
        function ($rootScope, $timeout, MapService) {
        'use strict';

        return {
            restrict: 'AE',
            templateUrl:  '/app/editor/position-list-directive.html',
            replace: true,
            scope: {
                feature: '=',
                editType: "@"
            },

            link: function (scope, element) {

                scope.editType = scope.editType || 'features';
                scope.positions = [];
                scope.type = scope.feature.getGeometry().getType();


                /** Emits a 'gj-editor-update' message to the parent directive **/
                function emit(type, origScope) {
                    scope.$emit('gj-editor-update', {
                        type: type,
                        featureId: scope.feature.getId(),
                        scope: scope.$id,
                        origScope: origScope
                    });
                }


                /** Will reload the tree data from the feature geometry **/
                scope.reloadFeature = function () {
                    var coordinates = [];
                    switch (scope.type) {
                        case 'Point':
                            coordinates = [ scope.feature.getGeometry().getCoordinates() ];
                            break;
                        case 'LineString':
                            coordinates = scope.feature.getGeometry().getCoordinates();
                            break;
                        case 'Polygon':
                            coordinates = scope.feature.getGeometry().getCoordinates()[0];
                            if (coordinates.length > 0) {
                                coordinates.pop();
                            }
                            break;
                    }
                    scope.positions.length = 0;
                    for (var x = 0; x < coordinates.length; x++) {
                        var lonLat = MapService.toLonLat(coordinates[x]);
                        scope.positions.push({
                            lat: lonLat[1],
                            lon: lonLat[0]
                        });
                    }
                    $rootScope.$$phase || $rootScope.$apply();
                };


                /** Called when the editor position has been updated **/
                scope.updateFeature = function () {
                    var coordinates = [];
                    angular.forEach(scope.positions, function (pos) {
                        if (pos.lat && pos.lon) {
                            var xy = MapService.fromLonLat([pos.lon, pos.lat]);
                            coordinates.push(xy)
                        }
                    });

                    var geom = scope.feature.getGeometry();
                    switch (geom.getType()) {
                        case 'Point':
                            geom.setCoordinates(coordinates[0]);
                            break;
                        case 'LineString':
                            geom.setCoordinates(coordinates);
                            break;
                        case 'Polygon':
                            if (coordinates.length > 0) {
                                coordinates.push(coordinates[0]);
                            }
                            geom.setCoordinates([coordinates]);
                            break;
                    }
                    emit('feature-modified', scope.feature.getId());
                };


                scope.reloadFeature();
                scope.$watch("positions", scope.updateFeature, true);

                /***************************/
                /** Event handling        **/
                /***************************/


                /** Listens for a 'gj-editor-update' event **/
                scope.$on('gj-editor-update', function(event, msg) {
                    // Do now process own events and only for the relevant feature
                    if (msg.scope == scope.$id || msg.origScope == scope.$id || msg.featureId != scope.feature.getId()) {
                        return;
                    }

                    switch (msg.type) {
                        case 'feature-added':
                        case 'feature-removed':
                        case 'feature-order-changed':
                        case 'zoom-feature':
                        case 'name-updated':
                            break;

                        case 'feature-modified':
                            scope.reloadFeature();
                            break;
                    }
                });


            }

        };
    }]);
