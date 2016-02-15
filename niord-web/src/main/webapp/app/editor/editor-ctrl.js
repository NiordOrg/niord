
/**
 * The Message Editor controller
 */
angular.module('niord.editor')

    /**
     * Main message editor controller
     */
    .controller('EditorCtrl', ['$scope', 'MapService',
        function ($scope, MapService) {
            'use strict';

            $scope.featureCollections = [];

            $scope.editFeatureCollection = function (index) {
                $scope.$broadcast('gj-editor-edit', "gj-editor-" + index);
            };

            $scope.loadFeatureCollections = function () {
                MapService.getAllFeatureCollections(function (featureCollections) {
                    $scope.featureCollections.length = 0;
                    angular.forEach(featureCollections, function (featureCollection) {
                        $scope.featureCollections.push(featureCollection);
                    });
                }, function (e) {
                    log.error("Error fetching shapes " + e)
                });
            };

            $scope.loadFeatureCollections();

            $scope.updateFeatureCollection = function (fc) {
                MapService.updateFeatureCollection(
                    fc,
                    $scope.loadFeatureCollections,
                    function (e) {log.error("Error fetching shapes " + e)}
                );
            };


            $scope.newFeatureCollection = function (fc) {
                MapService.createFeatureCollection(
                    fc,
                    $scope.loadFeatureCollections,
                    function (e) {log.error("Error fetching shapes " + e)}
                );
            };


            $scope.openThumbnail = function (fc) {
                window.open('/message-map-image/' + fc.id + '.png');
            }

        }])
        ;

