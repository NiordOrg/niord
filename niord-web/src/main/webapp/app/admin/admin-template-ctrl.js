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
     * TemplatesAdminCtrl
     * ********************************************************************************
     * Templates Admin Controller
     * Controller for the Admin Templates page
     */
    .controller('TemplatesAdminCtrl', ['$scope', '$timeout', '$uibModal', 'growl', 'AdminTemplateService', 'DialogService',
        function ($scope, $timeout, $uibModal, growl, AdminTemplateService, DialogService) {
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


            /** Loads the templates from the back-end */
            $scope.loadTemplates = function() {
                $scope.template = undefined;
                AdminTemplateService
                    .getTemplates()
                    .success(function (templates) {
                        $scope.templates = templates;
                    });
            };


            /** Focuses the template path input field **/
            $scope.focusPath = function () {
                $timeout(function () {
                    $('#templatePath').focus();
                }, 100)
            };


            /** Focuses the template path input field **/
            $scope.focusTemplateEditor = function () {
                $timeout(function () {
                    try { $scope.editor.focus(); } catch (e) {}
                }, 100)
            };


            /** Adds a new template **/
            $scope.addTemplate = function () {
                $scope.editMode = 'add';
                $scope.template = {
                    path: '',
                    template: ''
                };
                $scope.focusPath();
            };


            /** Edits a template **/
            $scope.editTemplate = function (template) {
                $scope.editMode = 'edit';
                $scope.template = angular.copy(template);
                $scope.focusTemplateEditor();
            };


            /** Copies a template **/
            $scope.copyTemplate = function (template) {
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
                growl.error("Error saving template", { ttl: 5000 });
            };


            /** Saves the current template being edited */
            $scope.saveTemplate = function () {

                if ($scope.template && $scope.editMode == 'add') {
                    AdminTemplateService
                        .createTemplate($scope.template)
                        .success($scope.loadTemplates)
                        .error($scope.displayError);
                } else if ($scope.template && $scope.editMode == 'edit') {
                    AdminTemplateService
                        .updateTemplate($scope.template)
                        .success($scope.loadTemplates)
                        .error($scope.displayError);
                }
            };


            /** Deletes the given template */
            $scope.deleteTemplate = function (template) {
                DialogService.showConfirmDialog(
                    "Delete Template?", "Delete template '" + template.path + "'?")
                    .then(function() {
                        AdminTemplateService
                            .deleteTemplate(template)
                            .success($scope.loadTemplates)
                            .error($scope.displayError);
                    });
            };


            /** Reload the templates from the file system **/
            $scope.reloadTemplates = function () {
                DialogService.showConfirmDialog(
                    "Reload Templates?", "Reload templates from the file system?")
                    .then(function() {
                        AdminTemplateService
                            .reloadTemplates()
                            .success($scope.loadTemplates);
                    });
            };


            /** Display the template history **/
            $scope.showTemplateHistory = function (template) {
                return $uibModal.open({
                    controller: "TemplateHistoryDialogCtrl",
                    templateUrl: "/app/admin/template-history-dialog.html",
                    size: 'lg',
                    resolve: {
                        template: function () { return template; }
                    }
                });

            }

        }])


    /*******************************************************************
     * TemplateHistoryDialogCtrl sub-controller that handles template history.
     *******************************************************************/
    .controller('TemplateHistoryDialogCtrl', ['$scope', '$rootScope', '$timeout', 'AdminTemplateService', 'template',
        function ($scope, $rootScope, $timeout, AdminTemplateService, template) {
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


            /** Loads the template history **/
            $scope.loadHistory = function () {
                if ($scope.template && $scope.template.id) {
                    AdminTemplateService.templateHistory($scope.template)
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
