
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
            $scope.initId = $stateParams.id || '';

            /*****************************/
            /** Initialize the editor   **/
            /*****************************/

            /** Called during initialization when the message has been loaded or instantiated */
            $scope.initMessage = function () {
                // Instantiate the feature collection from the message geometry
                if ($scope.message.geometry) {
                    $scope.featureCollection = $scope.message.geometry;
                }
            };


            /** Initialize the message editor */
            $scope.init = function () {
                // 1) The message ID may be specified
                if ($scope.initId) {
                    MessageService.details($scope.initId)
                        .success(function (message) {
                            $scope.message = message;
                            $scope.initMessage();
                        });

                } else {
                    // 2) The editor may be based on a template message, say, from the AtoN selection page
                    if ($state.includes('editor.template')) {
                        angular.copy($rootScope.templateMessage, $scope.message);
                    }
                    $scope.initMessage();
                }
            };
            $scope.init();


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
     * EditorCtrl sub-controller that handles message status changes.
     *******************************************************************/
    .controller('EditorStatusCtrl', ['$scope', '$rootScope', 'growl', 'MessageService', 'DialogService',
        function ($scope, $rootScope, growl, MessageService, DialogService) {
            'use strict';

            $scope.reloadMessage = function (msg) {
                // Call parent controller init() method
                $scope.init();
                growl.info(msg, {ttl: 3000});
            };


            /** Publish the message **/
            $scope.publish = function () {

                // First check that the message is valid
                if ($scope.message.status != 'VERIFIED') {
                    growl.error("Only verified draft messages can be published", {ttl: 5000});
                    return;
                }

                // TODO: MANY more checks

                DialogService.showConfirmDialog(
                    "Publish Message?", "Publish Message?")
                    .then(function() {
                        MessageService.updateMessageStatus($scope.message, 'PUBLISHED')
                            .success(function() { $scope.reloadMessage("Message published"); })
                            .error(function(err) {
                                growl.error("Publishing failed\n" + err, {ttl: 5000});
                            });
                    });
            };


            /** Delete the draft message **/
            $scope.delete = function () {
                if ($scope.message.status != 'DRAFT' && $scope.message.status != 'VERIFIED') {
                    growl.error("Only draft and verified messages can be deleted", {ttl: 5000});
                    return;
                }

                DialogService.showConfirmDialog(
                    "Delete draft?", "Delete draft?")
                    .then(function() {
                        MessageService.updateMessageStatus($scope.message, 'DELETED')
                            .success(function() { $scope.reloadMessage("Message deleted"); })
                            .error(function(err) {
                                growl.error("Deletion failed\n" + err, {ttl: 5000});
                            });
                    });
            };


            /** Copy the message **/
            $scope.copy = function () {
                DialogService.showConfirmDialog(
                    "Copy Message?", "Copy Message?")
                    .then(function() {
                        $rootScope.go('/editor/edit/copy/' + $scope.message.id + "/REFERENCE");
                    });
            };


            /** Cancel the message **/
            $scope.cancel = function () {
                if ($scope.message.status != 'PUBLISHED') {
                    growl.error("Only published messages can be cancelled", {ttl: 5000});
                    return;
                }

                var modalOptions = {
                    closeButtonText: 'Cancel',
                    actionButtonText: 'Confirm Cancellation',
                    headerText: 'Cancel Message',
                    cancelOptions: { createCancelMessage: true },
                    templateUrl: "cancelMessage.html"
                };

                DialogService.showDialog({}, modalOptions)
                    .then(function () {
                        MessageService.updateMessageStatus($scope.message, 'CANCELLED')
                            .success(function() {
                                if (modalOptions.cancelOptions.createCancelMessage) {
                                    $rootScope.go('/editor/edit/copy/' + $scope.message.id + "/CANCELLATION");
                                } else {
                                    $scope.reloadMessage("Message cancelled");
                                }
                            })
                            .error(function(err) {
                                growl.error("Cancellation failed\n" + err, {ttl: 5000});
                            });
                    });
            };


        }])



    /*******************************************************************
     * EditorCtrl sub-controller that handles message history.
     *******************************************************************/
    .controller('EditorHistoryCtrl', ['$scope', '$rootScope', '$timeout', 'MessageService',
        function ($scope, $rootScope, $timeout, MessageService) {
            'use strict';

            $scope.messageHistory = [];
            $scope.selectedHistory = [];

            /** Loads the message history **/
            $scope.loadHistory = function () {
                if ($scope.message.id) {
                    MessageService.messageHistory($scope.message.id)
                        .success(function (history) {
                            $scope.messageHistory.length = 0;
                            angular.forEach(history, function (hist) {
                                hist.selected = false;
                                $scope.messageHistory.push(hist);
                            })
                        });
                }
            };

            // Load the message history (after the message has been loaded)
            $timeout($scope.loadHistory, 200);


            /** updates the history selection **/
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

