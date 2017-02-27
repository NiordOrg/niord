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
     * FmTemplatesAdminCtrl
     * ********************************************************************************
     * Freemarker Templates Admin Controller
     * Controller for the Admin Freemarker Templates page
     */
    .controller('FmTemplatesAdminCtrl', ['$scope', '$stateParams', '$timeout', '$uibModal', 'growl',
                'AdminFmTemplateService', 'DialogService', 'UploadFileService',
        function ($scope, $stateParams, $timeout, $uibModal, growl,
                  AdminFmTemplateService, DialogService, UploadFileService) {
            'use strict';

            $scope.template = undefined; // The template being edited
            $scope.editMode = 'add';
            $scope.templates = [];
            $scope.search = '';

            $scope.fmEditorOptions = {
                useWrapMode : false,
                showGutter: true,
                mode: 'ftl',
                onLoad: function(editor) {
                    $scope.editor = editor;
                }
            };


            /** Loads the Freemarker templates from the back-end */
            $scope.loadFmTemplates = function(checkPathParam) {
                $scope.template = undefined;
                AdminFmTemplateService
                    .getFmTemplates()
                    .success(function (templates) {
                        $scope.templates = templates;

                        // If the page has been initialized with a "path" param, either edit the
                        // associated template, or add a new one.
                        var path = $stateParams.path;
                        if (checkPathParam && path) {
                            if (path.startsWith('/')) {
                                path = path.substring(1);
                            }
                            var pathTemplates = $.grep($scope.templates, function (t) {
                                return t.path == path;
                            });
                            if (pathTemplates.length == 1) {
                                $scope.editFmTemplate(pathTemplates[0]);
                            } else {
                                $scope.addFmTemplate();
                                $scope.template.path = path;
                            }
                        }
                    });
            };


            /** Focuses the Freemarker template path input field **/
            $scope.focusPath = function () {
                $timeout(function () {
                    $('#templatePath').focus();
                }, 100)
            };


            /** Focuses the Freemarker template path input field **/
            $scope.focusTemplateEditor = function () {
                $timeout(function () {
                    try { $scope.editor.focus(); } catch (e) {}
                }, 100)
            };


            /** Adds a new Freemarker template **/
            $scope.addFmTemplate = function () {
                $scope.editMode = 'add';
                $scope.template = {
                    path: '',
                    template: ''
                };
                $scope.focusPath();
            };


            /** Edits a Freemarker template **/
            $scope.editFmTemplate = function (template) {
                $scope.editMode = 'edit';
                $scope.template = angular.copy(template);
                $scope.focusTemplateEditor();
            };


            /** Copies a Freemarker template **/
            $scope.copyFmTemplate = function (template) {
                $scope.editMode = 'add';
                $scope.template = angular.copy(template);
                // Strip template name from path
                var x = $scope.template.path.lastIndexOf('/');
                if (x != -1) {
                    $scope.template.path = $scope.template.path.substring(0, x + 1);
                }
                $scope.focusPath();
            };


            /** Displays the error message */
            $scope.displayError = function () {
                growl.error("Error saving Freemarker template", { ttl: 5000 });
            };


            /** Saves the current Freemarker template being edited */
            $scope.saveFmTemplate = function () {

                if ($scope.template && $scope.editMode == 'add') {
                    AdminFmTemplateService
                        .createFmTemplate($scope.template)
                        .success($scope.loadFmTemplates)
                        .error($scope.displayError);
                } else if ($scope.template && $scope.editMode == 'edit') {
                    AdminFmTemplateService
                        .updateFmTemplate($scope.template)
                        .success($scope.loadFmTemplates)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given Freemarker template */
            $scope.deleteFmTemplate = function (template) {
                DialogService.showConfirmDialog(
                    "Delete Freemarker Template?", "Delete Freemarker template '" + template.path + "'?")
                    .then(function() {
                        AdminFmTemplateService
                            .deleteFmTemplate(template)
                            .success($scope.loadFmTemplates)
                            .error($scope.displayError);
                    });
            };


            /** Reload the Freemarker templates from the file system **/
            $scope.reloadFmTemplates = function () {
                DialogService.showConfirmDialog(
                    "Reload Freemarker Templates?", "Reload Freemarker templates from the file system?")
                    .then(function() {
                        AdminFmTemplateService
                            .reloadFmTemplates()
                            .success($scope.loadFmTemplates);
                    });
            };


            /** Generate an export file */
            $scope.exportFmTemplates = function () {
                AdminFmTemplateService
                    .exportTicket('admin')
                    .success(function (ticket) {
                        var link = document.createElement("a");
                        link.href = '/rest/fm-templates/all?ticket=' + ticket;
                        link.click();
                    });
            };


            /** Opens the upload-charts dialog **/
            $scope.uploadFmTemplatesDialog = function () {
                UploadFileService.showUploadFileDialog(
                    'Upload Freemarker Templates File',
                    '/rest/fm-templates/upload-templates',
                    'json');
            };


            /** Display the template history **/
            $scope.showFmTemplateHistory = function (template) {
                return $uibModal.open({
                    controller: "FmTemplateHistoryDialogCtrl",
                    templateUrl: "/app/sysadmin/fm-template-history-dialog.html",
                    size: 'lg',
                    resolve: {
                        template: function () { return template; }
                    }
                });

            }

        }])


    /*******************************************************************
     * FmTemplateHistoryDialogCtrl sub-controller that handles template history.
     *******************************************************************/
    .controller('FmTemplateHistoryDialogCtrl', ['$scope', '$rootScope', '$timeout', 'AdminFmTemplateService', 'template',
        function ($scope, $rootScope, $timeout, AdminFmTemplateService, template) {
            'use strict';

            $scope.template = template;
            $scope.templateHistory = [];
            $scope.selectedHistory = [];
            $scope.selectedTemplate = [];
            $scope.messageDiff = '';

            $scope.fmEditorOptions = {
                useWrapMode : false,
                showGutter: true,
                mode: 'ftl'
            };


            /** Loads the Freemarker template history **/
            $scope.loadHistory = function () {
                if ($scope.template && $scope.template.id) {
                    AdminFmTemplateService.getFmTemplateHistory($scope.template)
                        .success(function (history) {
                            $scope.templateHistory.length = 0;
                            angular.forEach(history, function (hist) {
                                hist.selected = false;
                                $scope.templateHistory.push(hist);
                            })
                        });
                }
            };

            // Load the template history (after the template has been loaded)
            $timeout($scope.loadHistory, 200);


            /** updates the history selection **/
            $scope.updateSelection = function () {
                $scope.selectedHistory.length = 0;
                $scope.selectedTemplate.length = 0;
                $scope.messageDiff = '';
                angular.forEach($scope.templateHistory, function (hist) {
                    if (hist.selected) {
                        $scope.selectedHistory.unshift(hist);
                        $scope.selectedTemplate.unshift(JSON.parse(hist.snapshot));
                    }
                });
                if($scope.selectedTemplate.length == 2) {
                    $timeout(function () {
                        var msg1 = $('#template_0').html();
                        var msg2 = $('#template_1').html();
                        $scope.messageDiff = htmldiff(msg1, msg2);
                    }, 300);
                }
            }

        }]);
