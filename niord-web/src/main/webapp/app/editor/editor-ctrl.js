
/**
 * The Message Editor controller
 */
angular.module('niord.editor')

    /**
     * Main message editor controller
     */
    .controller('EditorCtrl', ['$scope', '$rootScope', '$stateParams', '$state', '$uibModal', 'growl',
            'MessageService', 'MapService', 'UploadFileService',
        function ($scope, $rootScope, $stateParams, $state, $uibModal, growl,
                  MessageService, MapService, UploadFileService) {
            'use strict';

            $scope.message = {
                descs: []
            };
            $scope.featureCollection = {
                features: []
            };

            /*****************************/
            /** Initialize the editor   **/
            /*****************************/

            /** Initialize the message editor */
            $scope.init = function () {
                // Instantiate the feature collection from the message geometry
                if ($scope.message.geometry) {
                    $scope.featureCollection = $scope.message.geometry;
                }
            };

            // 1) The message ID may be specified
            if ($stateParams.id) {
                MessageService.details($stateParams.id)
                    .success(function (message) {
                        $scope.message = message;
                        $scope.init();
                    })
            } else {

                // 2) The editor may be based on a template message, say, from the AtoN selection page
                if ($state.includes('editor.template')) {
                    angular.copy($rootScope.templateMessage, $scope.message);
                }
                $scope.init();
            }


            $scope.editFeatureCollection = function () {
                $scope.$broadcast('gj-editor-edit', "message-geometry");
            };

            $scope.updateFeatureCollection = function (fc) {
                MapService.updateFeatureCollection(fc);
            };
            
            $scope.openThumbnail = function () {
                window.open('/rest/message-map-image/' + $stateParams.id + '.png');
            };


            /** Opens the message thumbnail dialog */
            $scope.messageThumbnailDialog = function () {
                $uibModal.open({
                    controller: "MessageThumbnailDialogCtrl",
                    templateUrl: "/app/editor/message-thumbnail-dialog.html",
                    size: 'sm',
                    resolve: {
                        message: function () { return $scope.message; }
                    }
                }).result.then(function (image) {
                    if (image && $scope.message.id) {
                        MessageService.changeMessageMapImage($scope.message.id, image)
                            .success(function () {
                                growl.info("Updated message thumbnail", { ttl: 3000 })
                            });
                    }
                });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadMessageThumbnailDialog = function () {
                if ($scope.message.id) {
                    UploadFileService.showUploadFileDialog(
                        'Upload thumbnail image',
                        '/rest/message-map-image/' + $scope.message.id,
                        'png,jpg,jpeg,gif');
                }
            };


            /** Clears the current message thumbnail **/
            $scope.clearMessageThumbnail = function () {
                if ($scope.message.id) {
                    MessageService.deleteMessageMapImage($scope.message.id)
                        .success(function () {
                            growl.info("Deleted message thumbnail", { ttl: 3000 })
                        });
                }
            }

        }])



    /*******************************************************************
     * EditorCtrl sub-controller that handles message history.
     *******************************************************************/
    .controller('EditorHistoryCtrl', ['$scope', '$rootScope', 'MessageService',
        function ($scope, $rootScope, MessageService) {
            'use strict';

            $scope.messageHistory = [];
            $scope.selectedHistory = [];

            // Load the message history
            if ($scope.message.id) {
                MessageService.messageHistory($scope.message.id)
                    .success(function (history) {
                        $scope.messageHistory.length = 0;
                        angular.forEach(history, function(hist) {
                            hist.selected = false;
                            $scope.messageHistory.push(hist);
                        })
                    });
            }


            // updates the history selection
            $scope.updateSelection = function () {
                $scope.selectedHistory.length = 0;
                angular.forEach($scope.messageHistory, function (hist) {
                    if (hist.selected) {
                        $scope.selectedHistory.unshift(hist);
                    }
                });
            }

        }])



    /*******************************************************************
     * Controller that handles the message Thumbnail dialog
     *******************************************************************/
    .controller('MessageThumbnailDialogCtrl', ['$scope', '$rootScope', 'message',
        function ($scope, $rootScope, message) {
            'use strict';

            $scope.messageList = [ message ];
            $scope.switcherLayers = [];
            $scope.showStdOSMLayer = $rootScope.osmSourceUrl && $rootScope.osmSourceUrl.length > 0;
            $scope.mapImageSize = $rootScope.mapImageSize || 256;

            /** Takes a thumbnail */
            $scope.thumbnail = function () {
                // Broadcast a take-thumbnail event to the message-thumbnail-layer.
                $scope.$broadcast('take-thumbnail', {});
            };


            /** Called when the thumbnail has been generated **/
            $scope.thumbnailGenerated = function (image) {
                $scope.$close(image);
            };


            $scope.updateVisibility = function (l) {
                l.layer.setVisible(l.visible)
            };

        }])

        ;

