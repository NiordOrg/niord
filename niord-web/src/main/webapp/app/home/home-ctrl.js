/**
 * The home controller
 */
angular.module('niord.home')
    .controller('HomeCtrl', ['$scope', '$timeout', '$stateParams', 'MessageService',
        function ($scope, $timeout, $stateParams, MessageService) {
            'use strict';

            $scope.messageList = [];

            $scope.init = function () {

                // Load the published messages
                MessageService.publicMessages()
                    .success(function (messages) {
                        $scope.messageList.length = 0;
                        for (var x = 0; x < messages.length; x++) {
                            $scope.messageList.push(messages[x]);
                        }
                        $scope.totalMessageNo = messages.length;
                    });

                // If specified in the URL, show the given message details
                if ($stateParams.messageId) {
                    $timeout(function() { MessageService.detailsDialog($stateParams.messageId) });
                }
            };

        }]);
