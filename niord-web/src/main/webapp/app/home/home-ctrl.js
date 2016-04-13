/**
 * The home controller
 */
angular.module('niord.home')
    .controller('HomeCtrl', ['$scope', 'MessageService',
        function ($scope, MessageService) {
            'use strict';

            $scope.messageList = [];

            $scope.init = function () {

                MessageService.search('viewMode=map&includeGeneral=false')
                    .success(function (result) {
                        $scope.messageList.length = 0;
                        for (var x = 0; x < result.data.length; x++) {
                            $scope.messageList.push(result.data[x]);
                        }
                        $scope.totalMessageNo = result.total;
                    });

            };

        }]);
