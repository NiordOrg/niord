
/**
 * The Message Editor controller
 */
angular.module('niord.editor')

    /**
     * Main message editor controller
     */
    .controller('EditorCtrl', ['$scope', '$rootScope', '$stateParams', '$state', '$uibModal', 'MessageService', 'MapService',
        function ($scope, $rootScope, $stateParams, $state, $uibModal, MessageService, MapService) {
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
                    if (image) {
                        // TODO: Save on backend.
                        var exportPNGElement = document.getElementById('snapshotBtn');
                        exportPNGElement.href = image;
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

