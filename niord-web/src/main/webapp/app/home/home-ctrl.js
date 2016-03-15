/**
 * The home controller
 */
angular.module('niord.home')
    .controller('HomeCtrl', ['$scope', 'MapService',
        function ($scope, MapService) {
            'use strict';

            $scope.messageList = [];

            $scope.init = function () {

                // TODO: Test using feature collections as messages
                MapService.getAllFeatureCollections().success(function (featureCollections) {
                    $scope.messageList.length = 0;
                    angular.forEach(featureCollections, function (featureCollection) {
                        $scope.messageList.push(featureCollection);
                    });
                });

            };

        }]);
