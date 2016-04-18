/**
 * The home controller
 */
angular.module('niord.home')
    .controller('HomeCtrl', ['$scope', 'MessageService',
        function ($scope, MessageService) {
            'use strict';

            $scope.messageList = [];

            $scope.init = function () {

                MessageService.publicMessages()
                    .success(function (messages) {
                        $scope.messageList.length = 0;
                        for (var x = 0; x < messages.length; x++) {
                            $scope.messageList.push(messages[x]);
                        }
                        $scope.totalMessageNo = messages.length;
                    });

            };

        }]);
