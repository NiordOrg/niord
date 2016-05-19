
/**
 * The Message Editor controller
 */
angular.module('niord.editor')

    /**
     * Main message editor controller
     */
    .controller('EditorCtrl', ['$scope', '$stateParams', 'MessageService', 'MapService',
        function ($scope, $stateParams, MessageService, MapService) {
            'use strict';

            $scope.message = {
                descs: []
            };
            $scope.featureCollection = {
                features: []
            };

            // Load the message details
            if ($stateParams.id) {
                MessageService.details($stateParams.id)
                    .success(function (message) {
                        $scope.message = message;
                        if (message.geometry) {
                            $scope.featureCollection = message.geometry;
                        } else {
                            $scope.featureCollection = {
                                features: []
                            };
                        }
                    })
            }


            $scope.featureCollections = [];

            $scope.editFeatureCollection = function () {
                $scope.$broadcast('gj-editor-edit', "message-geometry");
            };

            $scope.updateFeatureCollection = function (fc) {
                MapService.updateFeatureCollection(fc);
            };
            
            $scope.openThumbnail = function () {
                window.open('/rest/message-map-image/' + $stateParams.id + '.png');
            }

        }])
        ;

