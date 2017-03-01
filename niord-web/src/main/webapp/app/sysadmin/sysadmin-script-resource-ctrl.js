/*
 * Copyright 2017 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * The admin controllers.
 */
angular.module('niord.admin')

    /**
     * ********************************************************************************
     * ScriptResourcesAdminCtrl
     * ********************************************************************************
     * Script Resources Admin Controller
     * Controller for the Admin script resources page
     */
    .controller('ScriptResourcesAdminCtrl', ['$scope', '$stateParams', '$timeout', '$uibModal', 'growl',
                'AdminScriptResourceService', 'DialogService', 'UploadFileService',
        function ($scope, $stateParams, $timeout, $uibModal, growl,
                  AdminScriptResourceService, DialogService, UploadFileService) {
            'use strict';

            $scope.resource = undefined; // The resource being edited
            $scope.editMode = 'add';
            $scope.resources = [];
            $scope.search = '';

            $scope.fmEditorOptions = {
                'FM' : {
                    useWrapMode : false,
                    showGutter: true,
                    mode: 'ftl',
                    onLoad: function(editor) {
                        $scope.editor = editor;
                    }
                },
                'JS': {
                    useWrapMode : false,
                    showGutter: true,
                    mode: 'javascript',
                    onLoad: function(editor) {
                        $scope.editor = editor;
                    }
                }
            };

            $scope.paths = {
                'FM': {
                    name: 'Freemarker Path',
                    placeholder: 'resources/path.ftl',
                    pattern: '.+\.(ftl|FTL)'
                },
                'JS': {
                    name: 'JavaScript Path',
                    placeholder: 'resources/path.js',
                    pattern: '.+\.(js|JS)'
                }
            };


            /** Loads the script resources from the back-end */
            $scope.loadScriptResources = function(checkPathParam) {
                $scope.resource = undefined;
                AdminScriptResourceService
                    .getScriptResources()
                    .success(function (resources) {
                        $scope.resources = resources;

                        // If the page has been initialized with a "path" param, either edit the
                        // associated resource, or add a new one.
                        var path = $stateParams.path;
                        if (checkPathParam && path) {
                            if (path.startsWith('/')) {
                                path = path.substring(1);
                            }
                            var pathTemplates = $.grep($scope.resources, function (t) {
                                return t.path == path;
                            });
                            if (pathTemplates.length == 1) {
                                $scope.editScriptResource(pathTemplates[0]);
                            } else {
                                var type = path.toLowerCase().endsWith('.js') ? 'JS' : 'FM';
                                $scope.addScriptResource(type);
                                $scope.resource.path = path;
                            }
                        }
                    });
            };


            /** Focuses the script resource path input field **/
            $scope.focusPath = function () {
                $timeout(function () {
                    $('#resourcePath').focus();
                }, 100)
            };


            /** Focuses the script resource path input field **/
            $scope.focusTemplateEditor = function () {
                $timeout(function () {
                    try { $scope.editor.focus(); } catch (e) {}
                }, 100)
            };


            /** Adds a new script resource **/
            $scope.addScriptResource = function (type) {
                $scope.editMode = 'add';
                $scope.resource = {
                    type: type,
                    path: '',
                    content: ''
                };
                $scope.focusPath();
            };


            /** Edits a script resource **/
            $scope.editScriptResource = function (resource) {
                $scope.editMode = 'edit';
                $scope.resource = angular.copy(resource);
                $scope.focusTemplateEditor();
            };


            /** Copies a script resource **/
            $scope.copyScriptResource = function (resource) {
                $scope.editMode = 'add';
                $scope.resource = angular.copy(resource);
                // Strip resource name from path
                var x = $scope.resource.path.lastIndexOf('/');
                if (x != -1) {
                    $scope.resource.path = $scope.resource.path.substring(0, x + 1);
                }
                $scope.focusPath();
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving script resource", { ttl: 5000 });
            };


            /** Saves the current script resource being edited */
            $scope.saveScriptResource = function () {

                if ($scope.resource && $scope.editMode == 'add') {
                    AdminScriptResourceService
                        .createScriptResource($scope.resource)
                        .success($scope.loadScriptResources)
                        .error($scope.displayError);
                } else if ($scope.resource && $scope.editMode == 'edit') {
                    AdminScriptResourceService
                        .updateScriptResource($scope.resource)
                        .success($scope.loadScriptResources)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given script resource */
            $scope.deleteScriptResource = function (resource) {
                DialogService.showConfirmDialog(
                    "Delete script resource?", "Delete script resource '" + resource.path + "'?")
                    .then(function() {
                        AdminScriptResourceService
                            .deleteScriptResource(resource)
                            .success($scope.loadScriptResources)
                            .error($scope.displayError);
                    });
            };


            /** Reload the script resources from the file system **/
            $scope.reloadScriptResources = function () {
                DialogService.showConfirmDialog(
                    "Reload script resources?", "Reload script resources from the file system?")
                    .then(function() {
                        AdminScriptResourceService
                            .reloadScriptResources()
                            .success($scope.loadScriptResources);
                    });
            };


            /** Generate an export file */
            $scope.exportScriptResources = function () {
                AdminScriptResourceService
                    .exportTicket('admin')
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/script-resources/all?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadScriptResourcesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload script resources File',
                    '/rest/script-resources/upload-script-resources',
                    'json');
            };


            /** Display the resource history **/
            $scope.showScriptResourceHistory = function (resource) {
                return $uibModal.open({
                    controller: "ScriptResourceHistoryDialogCtrl",
                    templateUrl: "/app/sysadmin/script-resource-history-dialog.html",
                    size: 'lg',
                    resolve: {
                        resource: function () { return resource; }
                    }
                });

            }

        }])


    /*******************************************************************
     * ScriptResourceHistoryDialogCtrl controller that handles resource history.
     *******************************************************************/
    .controller('ScriptResourceHistoryDialogCtrl', ['$scope', '$rootScope', '$timeout', 'AdminScriptResourceService', 'resource',
        function ($scope, $rootScope, $timeout, AdminScriptResourceService, resource) {
            'use strict';

            $scope.resource = resource;
            $scope.resourceHistory = [];
            $scope.selectedHistory = [];
            $scope.selectedResource = [];
            $scope.messageDiff = '';

            $scope.fmEditorOptions = {
                'FM' : {
                    useWrapMode : false,
                    showGutter: true,
                    mode: 'ftl'
                },
                'JS': {
                    useWrapMode : false,
                    showGutter: true,
                    mode: 'javascript'
                }
            };


            /** Loads the script resource history **/
            $scope.loadHistory = function () {
                if ($scope.resource && $scope.resource.id) {
                    AdminScriptResourceService.getScriptResourceHistory($scope.resource)
                        .success(function (history) {
                            $scope.resourceHistory.length = 0;
                            angular.forEach(history, function (hist) {
                                hist.selected = false;
                                $scope.resourceHistory.push(hist);
                            })
                        });
                }
            };

            // Load the resource history (after the resource has been loaded)
            $timeout($scope.loadHistory, 200);


            /** updates the history selection **/
            $scope.updateSelection = function () {
                $scope.selectedHistory.length = 0;
                $scope.selectedResource.length = 0;
                $scope.messageDiff = '';
                angular.forEach($scope.resourceHistory, function (hist) {
                    if (hist.selected) {
                        $scope.selectedHistory.unshift(hist);
                        $scope.selectedResource.unshift(JSON.parse(hist.snapshot));
                    }
                });
                if($scope.selectedResource.length == 2) {
                    $timeout(function () {
                        var msg1 = $('#resource_0').html();
                        var msg2 = $('#resource_1').html();
                        $scope.messageDiff = htmldiff(msg1, msg2);
                    }, 300);
                }
            }

        }]);
